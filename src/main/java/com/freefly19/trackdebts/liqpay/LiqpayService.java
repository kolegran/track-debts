package com.freefly19.trackdebts.liqpay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freefly19.trackdebts.moneytransaction.MoneyTransaction;
import com.freefly19.trackdebts.moneytransaction.MoneyTransactionRepository;
import com.freefly19.trackdebts.security.UserRequestContext;
import com.freefly19.trackdebts.user.UserRepository;
import com.freefly19.trackdebts.util.DateTimeProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Base64Utils;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class LiqpayService {
    private final UserRepository userRepository;
    private final DateTimeProvider dateTimeProvider;
    private final LiqpayOrderRepository orderRepository;
    private final MoneyTransactionRepository moneyTransactionRepository;

    private final String publicKey;
    private final String privateKey;

    public LiqpayService(UserRepository userRepository,
                         DateTimeProvider dateTimeProvider,
                         LiqpayOrderRepository orderRepository,
                         MoneyTransactionRepository moneyTransactionRepository,
                         @Value("${LIQPAY_PUBLIC_KEY}") String publicKey,
                         @Value("${LIQPAY_PRIVATE_KEY}") String privateKey) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.dateTimeProvider = dateTimeProvider;
        this.moneyTransactionRepository = moneyTransactionRepository;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    @Transactional
    public LiqpayController.DataWithSig generate(long receiverId, BigDecimal amount, UserRequestContext context) {
        String receiverCard = userRepository.getOne(receiverId).getCardNumber();
        String orderId = UUID.randomUUID().toString();


        HashMap<String, Object> data = new HashMap<>();
        data.put("action", "p2p");
        data.put("version", 3);
        data.put("public_key", publicKey);
        data.put("amount", amount);
        data.put("currency", "UAH");
        data.put("receiver_card", receiverCard);
        data.put("order_id", orderId);

        LiqpayOrder order = LiqpayOrder.builder()
                .amount(amount)
                .receiver(userRepository.getOne(receiverId))
                .sender(userRepository.getOne(context.getId()))
                .code(orderId)
                .createdAt(context.timestamp())
                .build();
        orderRepository.save(order);

        try {
            String data64 = Base64Utils.encodeToString(new ObjectMapper().writeValueAsString(data).getBytes());
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update((privateKey + data64 + privateKey).getBytes());
            return new LiqpayController.DataWithSig(data64, Base64Utils.encodeToString(digest.digest()));
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public void onWebHook(String data) {
        final Map<String, Object> map;
        try {
            map = new ObjectMapper().readValue(data, Map.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        String orderId = map.get("order_id").toString();
        String status = map.get("status").toString();
        LiqpayOrder order = orderRepository.findByCode(orderId)
                .orElseThrow(() -> new IllegalStateException("There are no order with '" + orderId + "' order id"));

        LiqpayOrderLog orderLog = LiqpayOrderLog.builder()
                .order(order)
                .data(data)
                .status(status)
                .createdAt(new Timestamp(dateTimeProvider.now()))
                .build();

        order.getLogs().add(orderLog);
        orderRepository.save(order);

        if (!status.equalsIgnoreCase("success")) return;

        MoneyTransaction moneyTransaction = MoneyTransaction
                .builder()
                .sender(order.getSender())
                .receiver(order.getReceiver())
                .createdAt(new Timestamp(dateTimeProvider.now()))
                .amount(order.getAmount())
                .liqpayOrder(order)
                .build();
        moneyTransactionRepository.save(moneyTransaction);
    }
}
