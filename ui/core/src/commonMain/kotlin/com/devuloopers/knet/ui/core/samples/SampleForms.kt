package com.devuloopers.knet.ui.core.samples

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.checkbox.KNetCheckbox
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
public fun SampleForm(
    modifier: Modifier = Modifier
) {
    var textValue by remember { mutableStateOf("") }
    var isChecked by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        KNetTextField(
            value = textValue,
            onValueChange = { textValue = it },
            placeholder = "Sample Input Field",
            modifier = Modifier.padding(bottom = 8.dp)
        )
        KNetCheckbox(
            checked = isChecked,
            onCheckedChange = { isChecked = it },
            label = "Sample Checkbox Option",
            modifier = Modifier.padding(bottom = 12.dp)
        )
        KNetButton(onClick = {}) {
            Text("Submit Form")
        }
    }
}
