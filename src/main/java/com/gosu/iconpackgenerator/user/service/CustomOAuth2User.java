package com.gosu.iconpackgenerator.user.service;

import com.gosu.iconpackgenerator.user.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Getter
public class CustomOAuth2User implements OidcUser {
    
    private final Map<String, Object> attributes;
    private final User user;
    private final OidcIdToken idToken;
    private final OidcUserInfo userInfo;
    
    public CustomOAuth2User(Map<String, Object> attributes, User user) {
        this(attributes, user, null, null);
    }
    
    public CustomOAuth2User(Map<String, Object> attributes, User user, OidcIdToken idToken, OidcUserInfo userInfo) {
        this.attributes = attributes;
        this.user = user;
        this.idToken = idToken;
        this.userInfo = userInfo;
    }
    
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList(); // You can add roles here if needed
    }
    
    @Override
    public String getName() {
        return user.getEmail();
    }
    
    public String getEmail() {
        return user.getEmail();
    }
    
    public Long getUserId() {
        return user.getId();
    }
    
    @Override
    public Map<String, Object> getClaims() {
        return idToken != null ? idToken.getClaims() : attributes;
    }
    
    @Override
    public OidcUserInfo getUserInfo() {
        return userInfo;
    }
    
    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }
}
