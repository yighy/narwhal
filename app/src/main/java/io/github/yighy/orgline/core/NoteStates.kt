package io.github.yighy.orgline.core

import com.orgzly.org.utils.StateChangeLogic

/** State changes with Org semantics (CLOSED time, repeating tasks). */
object NoteStates {

    /** TODO ↔ DONE: done notes go back to the first to-do keyword, others to the first done keyword. */
    fun toggleTarget(note: OrgNote, keywords: Keywords): String? = when {
        note.isDone || note.state == null -> keywords.todo.firstOrNull()
        else -> keywords.done.firstOrNull()
    }

    data class Result(val edit: NoteEdit, val shifted: Boolean)

    /**
     * Sets [target] as Org does: marking a task done adds a CLOSED time, unless it has a
     * repeater, in which case its dates move forward and it stays to-do. Going back to to-do
     * removes the CLOSED time.
     */
    fun apply(edit: NoteEdit, target: String?, keywords: Keywords): Result {
        val logic = StateChangeLogic(keywords.done)
        logic.setState(
            target,
            edit.state,
            edit.scheduled?.let(OrgTime::range),
            edit.deadline?.let(OrgTime::range),
        )
        return Result(
            edit.copy(
                state = logic.state,
                scheduled = logic.scheduled?.toString(),
                deadline = logic.deadline?.toString(),
                closed = logic.closed?.toString(),
            ),
            logic.isShifted,
        )
    }
}
