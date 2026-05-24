package com.faforever.moderatorclient.config;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TemplateAndReasonConfig {
    private String name;
    private String reason;
    private String days;
    private String format;

    private List<TemplateAndReasonConfig> templates;
    private List<String> reasons;
}