package io.github.jakub_m_arch.voicemator.text

import android.content.Context
import io.github.jakub_m_arch.voicemator.dictation.TranscriptionSettings
import io.github.jakub_m_arch.voicemator.history.HistoryDatabase
import io.github.jakub_m_arch.voicemator.profiles.Profile

/**
 * Everything that shapes a dictation's text: the dictionary, the app profiles and the
 * transcription settings, read once at the start of a dictation.
 */
class DictationRules(
    private val dictionary: List<DictionaryEntry>,
    profiles: List<Profile>,
    /** Package name to profile ID. */
    appProfiles: Map<String, Long>,
    private val removeFillers: Boolean,
    private val finalPass: Boolean,
) {
    private val profilesByApp: Map<String, Profile> = profiles.associateBy { it.id }
        .let { byId -> appProfiles.mapNotNull { (app, id) -> byId[id]?.let { app to it } }.toMap() }

    /** The profile for the app with [packageName], or null if it has none. */
    fun profileFor(packageName: String?): Profile? = packageName?.let(profilesByApp::get)

    /** Cleans up text for [profile]: its own dictionary entries first, then the general ones. */
    fun postProcessor(profile: Profile?): TextPostProcessor {
        val (own, general) = dictionary
            .filter { it.profileId == null || it.profileId == profile?.id }
            .partition { it.profileId != null }
        return TextPostProcessor(
            replacements = (own + general).map(DictionaryEntry::toReplacement),
            removeFillers = removeFillers,
            style = profile?.textStyle ?: TextStyle(),
        )
    }

    fun finalPass(profile: Profile?): Boolean = profile?.finalPass ?: finalPass

    companion object {
        suspend fun load(context: Context): DictationRules {
            val database = HistoryDatabase.get(context)
            val settings = TranscriptionSettings.get(context)
            return DictationRules(
                dictionary = database.dictionary().getAll(),
                profiles = database.profiles().getAll(),
                appProfiles = database.profiles().getApps().associate { it.packageName to it.profileId },
                removeFillers = settings.removeFillers.value,
                finalPass = settings.finalPass.value,
            )
        }
    }
}
