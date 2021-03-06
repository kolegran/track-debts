package com.freefly19.trackdebts.bill.item.participant;

import com.freefly19.trackdebts.bill.item.BillItem;
import com.freefly19.trackdebts.user.User;
import lombok.*;

import javax.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter @Setter
@Entity
public class ItemParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private BillItem item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @Column(nullable = false)
    private BigDecimal coefficient;

    @Column(nullable = false)
    private Timestamp createdAt;
}
