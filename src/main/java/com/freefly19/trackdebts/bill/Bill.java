package com.freefly19.trackdebts.bill;

import com.freefly19.trackdebts.bill.item.BillItem;
import com.freefly19.trackdebts.bill.lock.BillLock;
import com.freefly19.trackdebts.user.User;
import lombok.*;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter @Setter
@Entity
public class Bill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Timestamp date;

    @Column(nullable = false)
    private Timestamp createdAt;

    @ManyToOne(optional = false)
    private User createdBy;

    @OneToOne(mappedBy = "bill")
    private BillLock billLock;

    @OneToMany(mappedBy = "bill")
    @OrderBy("id")
    private List<BillItem> items = new ArrayList<>();
}
