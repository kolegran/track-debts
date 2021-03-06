package com.freefly19.trackdebts.moneytransaction;

import com.freefly19.trackdebts.security.UserRequestContext;
import com.freefly19.trackdebts.user.User;
import com.freefly19.trackdebts.user.UserBalanceDto;
import com.freefly19.trackdebts.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class MoneyTransactionService {
    private final UserRepository userRepository;
    private final MoneyTransactionRepository moneyTransactionRepository;

    public Page<MoneyTransactionDto> findAll(Pageable pageable, UserRequestContext context) {
        Specification<MoneyTransaction> spec = Specification.where((r, q, cb) ->
                cb.or(
                        cb.equal(r.get("receiver").get("id"), context.getId()),
                        cb.equal(r.get("sender").get("id"), context.getId())
                )
        );

        pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("createdAt").descending());

        return moneyTransactionRepository.findAll(spec, pageable)
                .map(MoneyTransactionDto::new);
    }

    public Optional<String> receiveMoney(ReceiveMoneyCommand command, UserRequestContext context) {
        if (command.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.of("Amount should be an positive number");
        }

        if (context.getId() == command.getSenderId()) {
            return Optional.of("Current user cannot be sender");
        }

        Optional<User> oSender = userRepository.findById(command.getSenderId());
        if (!oSender.isPresent()) {
            return Optional.of("Sender not found");
        }

        moneyTransactionRepository.save(
                MoneyTransaction.builder()
                        .amount(command.getAmount())
                        .comment(command.getComment())
                        .createdAt(context.timestamp())
                        .receiver(context.toUser(userRepository))
                        .sender(oSender.get())
                        .build()
        );

        return Optional.empty();
    }


    @Transactional(readOnly = true)
    public List<UserBalanceDto> getBalance(UserRequestContext context) {
        Specification<MoneyTransaction> spec = Specification.where((r, q, cb) ->
                cb.or(
                        cb.equal(r.get("receiver").get("id"), context.getId()),
                        cb.equal(r.get("sender").get("id"), context.getId())
                )
        );

        Map<Long, BigDecimal> userBalanceMap = new HashMap<>();

        for (MoneyTransaction mt : moneyTransactionRepository.findAll(spec)) {
            if (mt.getReceiver().getId().equals(context.getId())) {
                userBalanceMap.put(mt.getSender().getId(), userBalanceMap.getOrDefault(mt.getSender().getId(), BigDecimal.ZERO).subtract(mt.getAmount()));
            } else {
                userBalanceMap.put(mt.getReceiver().getId(), userBalanceMap.getOrDefault(mt.getReceiver().getId(), BigDecimal.ZERO).add(mt.getAmount()));
            }
        }

        return userBalanceMap.keySet()
                .stream()
                .filter(key -> userBalanceMap.get(key).compareTo(BigDecimal.ZERO) != 0)
                .map(key -> new UserBalanceDto(userRepository.getOne(key), userBalanceMap.get(key)))
                .sorted(Comparator.comparing((UserBalanceDto o) -> o.getBalance().abs()).reversed())
                .collect(Collectors.toList());
    }
}
