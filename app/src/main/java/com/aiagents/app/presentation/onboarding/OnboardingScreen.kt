package com.aiagents.app.presentation.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiagents.app.R
import com.aiagents.app.presentation.agents.PersonalitySlider

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onNavigateToProviders: () -> Unit,
    onNavigateToMCP: () -> Unit,
    onNavigateToAgents: () -> Unit,
    onNavigateToWorkspaces: () -> Unit,
    onComplete: () -> Unit
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userNickname by viewModel.userNickname.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val cortexName by viewModel.cortexName.collectAsState()
    val sarcasm by viewModel.sarcasmLevel.collectAsState()
    val creativity by viewModel.creativityLevel.collectAsState()
    val formality by viewModel.formalityLevel.collectAsState()
    val empathy by viewModel.empathyLevel.collectAsState()
    val technical by viewModel.technicalPrecision.collectAsState()
    val lastStep = TOTAL_ONBOARDING_STEPS - 1

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Progress bar
            LinearProgressIndicator(
                progress = { (currentStep + 1) / TOTAL_ONBOARDING_STEPS.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Step indicator
            if (currentStep > 0) {
                Text(
                    text = stringResource(R.string.onboarding_step_of, currentStep + 1, TOTAL_ONBOARDING_STEPS),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }

            // Content
            AnimatedContent(
                targetState = currentStep,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 3 } + fadeOut())
                    }
                },
                label = "onboarding_step"
            ) { step ->
                when (step) {
                    0 -> WelcomeStep(
                        userName = userName,
                        userNickname = userNickname,
                        selectedLanguage = selectedLanguage,
                        onLanguageSelected = { viewModel.setLanguage(it) },
                        onNameChanged = { viewModel.setUserName(it) },
                        onNicknameChanged = { viewModel.setUserNickname(it) }
                    )
                    1 -> CortexPersonalityStep(
                        cortexName = cortexName,
                        onCortexNameChange = { viewModel.setCortexName(it) },
                        sarcasm = sarcasm,
                        creativity = creativity,
                        formality = formality,
                        empathy = empathy,
                        technical = technical,
                        onSarcasmChange = { viewModel.setSarcasm(it) },
                        onCreativityChange = { viewModel.setCreativity(it) },
                        onFormalityChange = { viewModel.setFormality(it) },
                        onEmpathyChange = { viewModel.setEmpathy(it) },
                        onTechnicalChange = { viewModel.setTechnicalPrecision(it) }
                    )
                    2 -> FeatureStep(
                        icon = Icons.Default.Cloud,
                        title = stringResource(R.string.onboarding_providers_title),
                        description = stringResource(R.string.onboarding_providers_desc),
                        actionLabel = stringResource(R.string.onboarding_configure_providers),
                        onAction = onNavigateToProviders
                    )
                    3 -> FeatureStep(
                        icon = Icons.Default.Extension,
                        title = stringResource(R.string.onboarding_mcp_title),
                        description = stringResource(R.string.onboarding_mcp_desc),
                        actionLabel = stringResource(R.string.onboarding_configure_mcp),
                        onAction = onNavigateToMCP
                    )
                    4 -> FeatureStep(
                        icon = Icons.Default.SmartToy,
                        title = stringResource(R.string.onboarding_agents_title),
                        description = stringResource(R.string.onboarding_agents_desc),
                        actionLabel = stringResource(R.string.onboarding_create_agents),
                        onAction = onNavigateToAgents
                    )
                    5 -> FinalStep(
                        icon = Icons.Default.Workspaces,
                        title = stringResource(R.string.onboarding_workspaces_title),
                        description = stringResource(R.string.onboarding_workspaces_desc),
                        actionLabel = stringResource(R.string.onboarding_setup_workspaces),
                        onAction = onNavigateToWorkspaces
                    )
                }
            }

            // Bottom navigation
            OnboardingBottomBar(
                currentStep = currentStep,
                lastStep = lastStep,
                isNextEnabled = currentStep != 0 || userName.isNotBlank(),
                onBack = { viewModel.previousStep() },
                onNext = {
                    if (currentStep == lastStep) {
                        onComplete()
                    } else {
                        viewModel.nextStep()
                    }
                },
                onSkip = { viewModel.nextStep() }
            )
        }
    }
}

@Composable
private fun WelcomeStep(
    userName: String,
    userNickname: String,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onNicknameChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Cortex icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Cortex speech bubble
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_cortex_intro),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Language selection
        Text(
            text = stringResource(R.string.onboarding_select_language),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LanguageCard(
                label = "English",
                flag = "\uD83C\uDDFA\uD83C\uDDF8",
                isSelected = selectedLanguage == "en",
                onClick = { onLanguageSelected("en") },
                modifier = Modifier.weight(1f)
            )
            LanguageCard(
                label = "Español",
                flag = "\uD83C\uDDEA\uD83C\uDDF8",
                isSelected = selectedLanguage == "es",
                onClick = { onLanguageSelected("es") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Name input
        OutlinedTextField(
            value = userName,
            onValueChange = onNameChanged,
            label = { Text(stringResource(R.string.onboarding_your_name)) },
            placeholder = { Text(stringResource(R.string.onboarding_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Nickname input
        OutlinedTextField(
            value = userNickname,
            onValueChange = onNicknameChanged,
            label = { Text(stringResource(R.string.onboarding_your_nickname)) },
            placeholder = { Text(stringResource(R.string.onboarding_nickname_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // Dynamic greeting from Cortex
        val displayName = userNickname.ifBlank { userName }
        AnimatedVisibility(visible = displayName.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.onboarding_greeting, displayName),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LanguageCard(
    label: String,
    flag: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface

    Card(
        modifier = modifier
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = flag, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun CortexPersonalityStep(
    cortexName: String,
    onCortexNameChange: (String) -> Unit,
    sarcasm: Int,
    creativity: Int,
    formality: Int,
    empathy: Int,
    technical: Int,
    onSarcasmChange: (Int) -> Unit,
    onCreativityChange: (Int) -> Unit,
    onFormalityChange: (Int) -> Unit,
    onEmpathyChange: (Int) -> Unit,
    onTechnicalChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_cortex_personality_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_cortex_personality_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = cortexName,
            onValueChange = onCortexNameChange,
            label = { Text(stringResource(R.string.onboarding_cortex_name)) },
            placeholder = { Text(stringResource(R.string.onboarding_cortex_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        PersonalitySlider(
            icon = "\uD83C\uDFAD",
            label = stringResource(R.string.onboarding_sarcasm),
            value = sarcasm,
            onValueChange = onSarcasmChange,
            lowLabel = stringResource(R.string.onboarding_sarcasm_low),
            highLabel = stringResource(R.string.onboarding_sarcasm_high)
        )

        Spacer(modifier = Modifier.height(16.dp))

        PersonalitySlider(
            icon = "\uD83C\uDFA8",
            label = stringResource(R.string.onboarding_creativity),
            value = creativity,
            onValueChange = onCreativityChange,
            lowLabel = stringResource(R.string.onboarding_creativity_low),
            highLabel = stringResource(R.string.onboarding_creativity_high)
        )

        Spacer(modifier = Modifier.height(16.dp))

        PersonalitySlider(
            icon = "\uD83D\uDC54",
            label = stringResource(R.string.onboarding_formality),
            value = formality,
            onValueChange = onFormalityChange,
            lowLabel = stringResource(R.string.onboarding_formality_low),
            highLabel = stringResource(R.string.onboarding_formality_high)
        )

        Spacer(modifier = Modifier.height(16.dp))

        PersonalitySlider(
            icon = "\uD83D\uDC9D",
            label = stringResource(R.string.onboarding_empathy),
            value = empathy,
            onValueChange = onEmpathyChange,
            lowLabel = stringResource(R.string.onboarding_empathy_low),
            highLabel = stringResource(R.string.onboarding_empathy_high)
        )

        Spacer(modifier = Modifier.height(16.dp))

        PersonalitySlider(
            icon = "\uD83C\uDFAF",
            label = stringResource(R.string.onboarding_technical),
            value = technical,
            onValueChange = onTechnicalChange,
            lowLabel = stringResource(R.string.onboarding_technical_low),
            highLabel = stringResource(R.string.onboarding_technical_high)
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureStep(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Feature icon
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = actionLabel, style = MaterialTheme.typography.titleSmall)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FinalStep(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Workspace icon
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = actionLabel, style = MaterialTheme.typography.titleSmall)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Ready message
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_ready_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.onboarding_ready_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun OnboardingBottomBar(
    currentStep: Int,
    lastStep: Int,
    isNextEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Progress dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(TOTAL_ONBOARDING_STEPS) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (index == currentStep) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == currentStep) MaterialTheme.colorScheme.primary
                            else if (index < currentStep) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            if (currentStep > 0) {
                TextButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.onboarding_back))
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Row {
                // Skip button (feature steps 2-4, not on welcome, personality, or final)
                if (currentStep in 2..(lastStep - 1)) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Next / Get Started button
                Button(
                    onClick = onNext,
                    enabled = isNextEnabled,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (currentStep == lastStep) {
                            stringResource(R.string.onboarding_get_started)
                        } else {
                            stringResource(R.string.onboarding_continue)
                        }
                    )
                    if (currentStep < lastStep) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
