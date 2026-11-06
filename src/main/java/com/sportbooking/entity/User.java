package com.sportbooking.entity;

import com.sportbooking.common.AuditableEntity;
import com.sportbooking.entity.enums.Role;
import com.sportbooking.entity.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Không dùng @Setter: trạng thái chỉ thay đổi qua các method nghiệp vụ có tên rõ ràng
 * (lock, promoteToOwner...). Đọc code là biết entity có thể thay đổi theo cách nào.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // JPA cần constructor rỗng, nhưng code ngoài không nên dùng
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    private User(String email, String passwordHash, String fullName, String phone, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
        this.role = role;
        this.status = UserStatus.ACTIVE;
    }

    public static User newCustomer(String email, String passwordHash, String fullName, String phone) {
        return new User(email, passwordHash, fullName, phone, Role.CUSTOMER);
    }

    public static User newAdmin(String email, String passwordHash, String fullName) {
        return new User(email, passwordHash, fullName, null, Role.ADMIN);
    }

    public boolean isLocked() {
        return status == UserStatus.LOCKED;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public void updateProfile(String fullName, String phone) {
        this.fullName = fullName;
        this.phone = phone;
    }

    public void promoteToOwner() {
        this.role = Role.OWNER;
    }

    public void changeStatus(UserStatus status) {
        this.status = status;
    }
}
