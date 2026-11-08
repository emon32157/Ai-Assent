package app.shilex.android.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.shilex.android.R
import app.shilex.android.task.Risk
import app.shilex.android.task.Task
import app.shilex.android.task.TaskState

/** The task in progress: its steps as they really happened, and what is true now. */
@Composable
fun TaskCard(task: Task, modifier: Modifier = Modifier) {
    val (labelRes, color) = stateLabel(task.state)
    Column(
        modifier
            .fillMaxWidth()
            .background(Surface, MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (task.risk >= Risk.High) {
                Pill(
                    if (task.risk == Risk.Financial) stringResource(R.string.task_payment_you) else stringResource(R.string.badge_needs_you),
                    Warning,
                    Modifier.padding(end = 6.dp),
                    dot = false,
                )
            }
            Pill(stringResource(labelRes), color)
        }
        task.steps.forEachIndexed { i, step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepMark(step.done)
                Text(
                    "${i + 1}. ${step.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (step.done == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        if (task.detail.isNotEmpty()) {
            Text(task.detail, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun StepMark(done: Boolean?) {
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        when (done) {
            null -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Accent)
            true -> Box(Modifier.size(10.dp).background(Accent, CircleShape))
            false -> Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, CircleShape))
        }
    }
}

@Composable
private fun stateLabel(state: TaskState): Pair<Int, Color> = when (state) {
    TaskState.Understanding -> R.string.task_state_understanding to Accent
    TaskState.Planning -> R.string.task_state_planning to Warning
    TaskState.AwaitingConfirmation -> R.string.task_state_awaiting to Warning
    TaskState.Executing -> R.string.task_state_executing to Accent
    TaskState.WaitingForYou -> R.string.task_state_waiting_you to Warning
    TaskState.Completed -> R.string.task_state_completed to Accent
    TaskState.Failed -> R.string.task_state_failed to MaterialTheme.colorScheme.error
}
