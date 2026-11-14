package com.metrolist.music.bcmix.auth

/**
 * Credential store for the BitChord settings. NOT PORTED: BitChord keeps
 * these in an encrypted store; Metrolist does not, so the values live only
 * in memory for the life of the process. Used only for the password mirrors.
 */
class AuthStore {
    var webdavPassword: String? = null
    var smbPassword: String? = null
    var discordToken: String? = null
}
