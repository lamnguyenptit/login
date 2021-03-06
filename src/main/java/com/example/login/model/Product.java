package com.example.login.model;

import lombok.*;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "product")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
//@EqualsAndHashCode
public class Product extends IdBaseEntity  implements Serializable{
    private static final long serialVersionUID = -3822831474371253454L;

    @Column(length = 100)
    private String name;

    private Float cost;

    @Column(name = "create_time")
    private Date createTime;

    @Column(name = "update_time")
    private Date updateTime;

    @Column(name = "discount_percent")
    private Float discountPercent;

    private boolean enabled;


    @Column(length = 4096)
    private String description;

    @Column(length = 256)
    private String image;

    @Column(name="in_stock")
    private boolean inStock;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(nullable = false)
    private Float price;


    @ManyToOne
    @JoinColumn(name = "publisher_id")
    private Publisher publisher;

    public Product(Integer id){
        this.id = id;
    }

    @OneToMany(mappedBy = "product")
    private List<CartAndProduct> productAssoc;

    @Transient
    public float getDiscountPrice() {
        if(discountPercent > 0) {
            return price * ((100 - discountPercent) / 100);
        }
        return this.price;
    }

    public Product(String name, Float cost, Date createTime, Float discountPercent, boolean enabled, String description, String image, boolean inStock, int quantity, Float price, Set<Category> categories, Publisher publisher) {
        this.name = name;
        this.cost = cost;
        this.createTime = createTime;
        this.discountPercent = discountPercent;
        this.enabled = enabled;
        this.description = description;
        this.image = image;
        this.inStock = inStock;
        this.quantity = quantity;
        this.price = price;
        this.categories = categories;
        this.publisher = publisher;
    }

    @ManyToMany
    @JoinTable(
            name = "product_category",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.getId() == null) ? 0 : this.getId().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Product other = (Product) obj;
        if (this.getId() == null) {
            if (other.getId() != null)
                return false;
        } else if (!this.getId().equals(other.getId()))
            return false;
        return true;
    }
}
