package com.gosu.icon_pack_generator.controller;

import com.gosu.icon_pack_generator.dto.IconDto;
import com.gosu.icon_pack_generator.entity.GeneratedIcon;
import com.gosu.icon_pack_generator.entity.User;
import com.gosu.icon_pack_generator.repository.GeneratedIconRepository;
import com.gosu.icon_pack_generator.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private GeneratedIconRepository generatedIconRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/user/icons")
    public ResponseEntity<List<IconDto>> getUserIcons() {
        // Hardcoded user for now
        String userEmail = "default@iconpack.com";
        Optional<User> userOptional = userRepository.findByEmail(userEmail);

        if (userOptional.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        User user = userOptional.get();
        List<GeneratedIcon> icons = generatedIconRepository.findByUserOrderByCreatedAtDesc(user);

        List<IconDto> iconDtos = icons.stream()
                .map(icon -> new IconDto(
                        icon.getFilePath(),
                        icon.getDescription(),
                        icon.getServiceSource(),
                        icon.getRequestId(),
                        icon.getIconType(),
                        icon.getTheme()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(iconDtos);
    }
}
