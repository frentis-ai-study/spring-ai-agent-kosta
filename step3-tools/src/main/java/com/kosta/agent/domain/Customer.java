package com.kosta.agent.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 20)
    private String tier; // BASIC, GOLD, VIP

    protected Customer() {}

    public Customer(String email, String name, String tier) {
        this.email = email;
        this.name = name;
        this.tier = tier;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getTier() { return tier; }
}
