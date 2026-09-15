// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.solver

import kotlinx.serialization.Serializable

/**
 * Frequency classification of a found word (dossier 1.7): [Common] words are shown in a normal
 * font, [Expert] ones in italics. The dossier does not fix a wire token for this field, so the
 * Kotlin enum name doubles as the serialized value.
 */
@Serializable
enum class WordTier { Common, Expert }
