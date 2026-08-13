package com.muxy.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La limpieza de títulos es conservadora a propósito: quitar de más se carga
 * parte del nombre real de la canción, y eso no se nota hasta que la librería ya
 * está llena. Estos casos fijan justo esa frontera.
 */
class TrackNamingTest {

    @Test
    fun `separa artista y titulo`() {
        val names = TrackNaming.parse("Extremoduro - So pena", uploader = null)
        assertEquals("Extremoduro", names.artist)
        assertEquals("So pena", names.title)
    }

    @Test
    fun `quita el ruido de promocion`() {
        val names = TrackNaming.parse("Rosalia - Malamente (Official Video) [4K]", uploader = null)
        assertEquals("Rosalia", names.artist)
        assertEquals("Malamente", names.title)
    }

    @Test
    fun `conserva los parentesis que son parte del nombre`() {
        // Lo que más duele de una limpieza agresiva: perder feat, remix o
        // acústico deja dos canciones distintas con el mismo nombre.
        listOf("(feat. Bad Bunny)", "(Remix)", "(Acústico)").forEach { suffix ->
            val names = TrackNaming.parse("Alguien - Cancion $suffix", uploader = null)
            assertEquals("Cancion $suffix", names.title)
        }
    }

    @Test
    fun `usa el canal Topic como artista cuando el titulo no lo trae`() {
        val names = TrackNaming.parse("So pena", uploader = "Extremoduro - Topic")
        assertEquals("Extremoduro", names.artist)
        assertEquals("So pena", names.title)
    }

    @Test
    fun `sin separador ni canal se queda sin artista`() {
        val names = TrackNaming.parse("Una cancion cualquiera", uploader = null)
        assertNull(names.artist)
        assertEquals("Una cancion cualquiera", names.title)
    }

    @Test
    fun `el guion sin espacios no parte el titulo`() {
        // "Spider-Man" no es "Spider" de "Man": el separador exige espacios.
        val names = TrackNaming.parse("Spider-Man", uploader = null)
        assertNull(names.artist)
        assertEquals("Spider-Man", names.title)
    }

    @Test
    fun `el nombre de archivo quita lo que FAT no admite`() {
        val name = TrackNaming.toFileName("AC/DC: Back?", artist = "AC/DC")
        assertEquals(false, name.contains("/"))
        assertEquals(false, name.contains("?"))
        assertEquals(false, name.contains(":"))
    }

    @Test
    fun `un titulo vacio no deja el archivo sin nombre`() {
        assertEquals("cancion", TrackNaming.toFileName("", artist = null))
    }
}
