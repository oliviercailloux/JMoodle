package io.github.oliviercailloux.jmoodle.raw;

import java.util.Optional;

/** Differs from SubmissionPluginData */
@SuppressWarnings("checkstyle:RecordComponentName")
public record GradePluginData (Optional<Feedback> assignfeedbackcomments_editor,
    Optional<Integer> files_filemanager) {

}
