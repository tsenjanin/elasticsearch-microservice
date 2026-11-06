package dev.elasticsearchmicroservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogSource {
    private String group;
    private String name;
    private String url;
    private String index;
}
