package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.NovaBorder
import com.example.ui.theme.NovaCyanNeon
import com.example.ui.theme.NovaObsidian
import com.example.ui.theme.NovaRoseAlert
import com.example.ui.theme.NovaSkyPlasma
import com.example.ui.theme.NovaSurface
import com.example.ui.theme.NovaSurfaceElevated
import com.example.ui.theme.NovaTealGlow
import com.example.ui.theme.NovaTextPrimary
import com.example.ui.theme.NovaTextSecondary

@Composable
fun BottomVoiceBar(
    inputText: String,
    isListeningVoice: Boolean,
    isThinking: Boolean,
    liveVoiceTranscript: String,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onRecordVoiceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val micBg by animateColorAsState(
        targetValue = if (isListeningVoice) NovaRoseAlert else NovaSurfaceElevated,
        label = "micBg"
    )
    val micTint by animateColorAsState(
        targetValue = if (isListeningVoice) NovaObsidian else NovaCyanNeon,
        label = "micTint"
    )
    val canSend = inputText.isNotBlank() && !isThinking

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        AnimatedVisibility(visible = isListeningVoice) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, start = 6.dp, end = 6.dp)
                    .background(
                        color = NovaSurfaceElevated.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .border(1.dp, NovaTealGlow.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = NovaTealGlow,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = liveVoiceTranscript.ifBlank { "Mendengarkan suara Anda…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = NovaTextPrimary,
                    maxLines = 2
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = NovaSurface.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(28.dp)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(NovaBorder, NovaCyanNeon.copy(alpha = 0.45f), NovaBorder)
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = stringResource(R.string.input_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = NovaTextSecondary
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = NovaTextPrimary),
                    cursorBrush = SolidColor(NovaCyanNeon),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { if (canSend) onSendClick() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("message_input_field")
                )
            }

            IconButton(
                onClick = onRecordVoiceClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(micBg)
                    .border(
                        width = 1.dp,
                        color = if (isListeningVoice) NovaRoseAlert else NovaCyanNeon.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
                    .testTag("record_audio_button")
            ) {
                Icon(
                    imageVector = if (isListeningVoice) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = stringResource(
                        if (isListeningVoice) R.string.stop_voice_desc else R.string.record_voice_desc
                    ),
                    tint = micTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onSendClick,
                enabled = canSend,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) {
                            Brush.linearGradient(listOf(NovaCyanNeon, NovaSkyPlasma))
                        } else {
                            Brush.linearGradient(listOf(NovaSurfaceElevated, NovaSurfaceElevated))
                        }
                    )
                    .testTag("send_message_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.send_message_desc),
                    tint = if (canSend) NovaObsidian else NovaTextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
