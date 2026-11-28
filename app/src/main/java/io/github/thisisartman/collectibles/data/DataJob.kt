package io.github.thisisartman.collectibles.data

/** The long jobs started from Settings, Backup and data (#217): one runs at a time, as they share the backup lock. */
enum class DataJob { BACKUP, RESTORE, PHOTO_FILE, MERGE }

/** Where a [DataJob] is: reading the chosen file, saving a copy of what is here first, the job itself, its photos. */
enum class DataStep { READING, COPY, WORKING, PHOTOS }

/** How far a job is: its step, and the photos done of the total for a step that counts them (0 of 0 when not). */
typealias OnProgress = (step: DataStep, done: Int, total: Int) -> Unit

val NO_PROGRESS: OnProgress = { _, _, _ -> }
