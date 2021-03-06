package com.example.login.model.dto;

import com.example.login.model.*;
import lombok.*;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode
public class UserDto {
    private int id;

    @Size(max = 20, message = "Name must less than 20 characters")
    @NotBlank(message = "Username cannot empty")
    private String username;

    @Email(message = "Email isn't valid!")
    private String email;

    @Size(max = 20, message = "Password must be between 3 and 20 characters")
    private String password;

    @Size(max = 50, message = "Name must less than 50 characters")
    @NotBlank(message = "Name cannot empty")
    private String name;

    @Pattern(regexp = "^[\\d]*$", message = "Phone must be digits")
    @Size(max = 15, message = "Phone must be less than 15 characters")
    private String phone;

    @Size(max = 50, message = "Address must less than 50 characters")
    private String address;

    private String image;

    @Valid
    private List<CompanyDto> companies;

    @Valid
    private List<SchoolDto> schools;

    @Valid
    private List<DegreeDto> degrees;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private Provider provider;

    public String getProviderType(){
        return this.provider.name();
    }

    private boolean locked;
    private boolean enabled;
    private ConfirmationTokenDto confirmationTokenDto;
}
