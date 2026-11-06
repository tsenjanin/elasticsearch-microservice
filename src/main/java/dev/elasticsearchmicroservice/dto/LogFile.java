package dev.elasticsearchmicroservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogFile {
    private String name;
    private LocalDate date;
    private Integer index;
    private Boolean active;
    private Supplier<List<String>> lines;
}
