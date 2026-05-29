package com.faforever.moderatorclient.ui;

import com.faforever.moderatorclient.ui.domain.UserNoteFX;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

final class ViewHelperFormatters {
    private static final DateTimeFormatter DT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private ViewHelperFormatters() {
    }

    static String formatNotes(List<UserNoteFX> notes) {
        if (notes.isEmpty()) return "No notes";
        return notes.stream()
                .sorted(Comparator.<UserNoteFX, OffsetDateTime>comparing(
                        note -> note.getCreateTime() != null ? note.getCreateTime() : OffsetDateTime.MIN)
                        .reversed())
                .map(note -> {
                    String date = note.getCreateTime() != null ? DT_DATE.format(note.getCreateTime()) : "?";
                    String author = note.getAuthor() != null && note.getAuthor().getLogin() != null
                            ? note.getAuthor().getLogin() : "?";
                    return "[" + date + "] " + author + ": " + note.getNote();
                })
                .collect(Collectors.joining("\n\n"));
    }
}
