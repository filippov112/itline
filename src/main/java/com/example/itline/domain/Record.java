package com.example.itline.domain;

import jakarta.persistence.*;

/**
 * Строка таблицы records.
 */
@Entity
@Table(name = "records")
public class Record {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, length = 16)
    private String icon;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    protected Record() { }

    public Record(boolean active, String icon, String name, String description) {
        this.active = active;
        this.icon = icon;
        this.name = name;
        this.description = description;
    }


    public Long getId() { return id; }
    public boolean isActive() { return active; }
    public String getIcon() { return icon; }
    public String getName() { return name; }
    public String getDescription() { return description; }

    public void setActive(boolean active) { this.active = active; }
    public void setIcon(String icon) { this.icon = icon; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
}