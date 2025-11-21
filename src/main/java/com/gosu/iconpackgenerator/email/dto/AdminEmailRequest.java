package com.gosu.iconpackgenerator.email.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminEmailRequest {

    private String subject;
    private String htmlBody;
    private RecipientScope recipientScope;
    private String manualEmail;

    public RecipientScope getRecipientScopeOrDefault() {
        return recipientScope != null ? recipientScope : RecipientScope.ME;
    }

    public enum RecipientScope {
        ME,
        EVERYBODY,
        SPECIFIC
    }
}
