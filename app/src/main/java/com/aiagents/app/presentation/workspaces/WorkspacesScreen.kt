package com.aiagents.app.presentation.workspaces

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiagents.app.domain.model.Workspace
import com.aiagents.app.ui.theme.ShapeTokens

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WorkspacesScreen(
    onWorkspaceClick: (Workspace) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: WorkspacesViewModel = hiltViewModel()
) {
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Workspaces",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showCreateDialog() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nuevo Workspace") },
                shape = ShapeTokens.FloatingActionButton,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { padding ->
        if (workspaces.isEmpty()) {
            EmptyWorkspacesState(
                onCreateClick = { viewModel.showCreateDialog() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding)
            ) {
                items(
                    items = workspaces,
                    key = { it.id }
                ) { workspace ->
                    WorkspaceCardWithMenu(
                        workspace = workspace,
                        isExporting = uiState.exportingWorkspaceId == workspace.id,
                        onClick = { onWorkspaceClick(workspace) },
                        onLongClick = { viewModel.showContextMenu(workspace) },
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    )
                }
            }
        }

        // Create Dialog
        if (uiState.showCreateDialog) {
            CreateWorkspaceDialog(
                formState = uiState.formState,
                isLoading = uiState.isLoading,
                onDismiss = { viewModel.hideCreateDialog() },
                onCreate = {
                    viewModel.createWorkspace { workspaceId ->
                        // The workspace will be available in the workspaces flow
                        // Navigate after creation
                    }
                },
                onFormUpdate = { viewModel.updateFormState(it) }
            )
        }

        // Delete Dialog
        if (uiState.showDeleteDialog && uiState.workspaceToDelete != null) {
            DeleteWorkspaceDialog(
                workspace = uiState.workspaceToDelete!!,
                onDismiss = { viewModel.hideDeleteDialog() },
                onConfirm = { viewModel.deleteWorkspace() }
            )
        }

        // Rename Dialog
        if (uiState.showRenameDialog && uiState.workspaceToRename != null) {
            RenameWorkspaceDialog(
                formState = uiState.formState,
                isLoading = uiState.isLoading,
                onDismiss = { viewModel.hideRenameDialog() },
                onConfirm = { viewModel.renameWorkspace() },
                onFormUpdate = { viewModel.updateFormState(it) }
            )
        }

        // Change Storage Dialog
        if (uiState.showChangeStorageDialog && uiState.workspaceToChangeStorage != null) {
            ChangeStorageDialog(
                formState = uiState.formState,
                isLoading = uiState.isLoading,
                onDismiss = { viewModel.hideChangeStorageDialog() },
                onConfirm = { viewModel.updateWorkspaceStorage() },
                onFormUpdate = { viewModel.updateFormState(it) }
            )
        }

        // Context Menu
        val selectedWorkspace = uiState.selectedWorkspace
        if (uiState.showContextMenu && selectedWorkspace != null) {
            WorkspaceContextMenu(
                workspace = selectedWorkspace,
                onDismiss = { viewModel.hideContextMenu() },
                onRename = { viewModel.showRenameDialog(selectedWorkspace) },
                onExport = { viewModel.exportWorkspace(selectedWorkspace) },
                onChangeStorage = { viewModel.showChangeStorageDialog(selectedWorkspace) },
                onDelete = { viewModel.showDeleteDialog(selectedWorkspace) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkspaceCardWithMenu(
    workspace: Workspace,
    isExporting: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = ShapeTokens.Card,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = workspace.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Indicador de almacenamiento externo
                if (workspace.externalStorageUri != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Outlined.SdStorage,
                        contentDescription = "Almacenamiento externo",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (workspace.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = workspace.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Hint about long press
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Mantén presionado para más opciones",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            // AGENTES.md generation indicator
            if (isExporting) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Generando AGENTES.md...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WorkspaceContextMenu(
    workspace: Workspace,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onChangeStorage: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ShapeTokens.Dialog,
        title = { Text(workspace.name) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ListItem(
                    headlineContent = { Text("Renombrar") },
                    leadingContent = {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.combinedClickable(onClick = onRename)
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("Generar AGENTES.md") },
                    supportingContent = { Text("IA analiza y genera contexto del proyecto") },
                    leadingContent = {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    },
                    modifier = Modifier.combinedClickable(onClick = onExport)
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("Cambiar almacenamiento") },
                    supportingContent = {
                        Text(
                            if (workspace.externalStorageUri != null) "Usando carpeta del teléfono"
                            else "Usando almacenamiento de la app"
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Outlined.SdStorage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    },
                    modifier = Modifier.combinedClickable(onClick = onChangeStorage)
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("Eliminar") },
                    leadingContent = {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.combinedClickable(onClick = onDelete)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun EmptyWorkspacesState(
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No hay workspaces",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Crea un workspace para comenzar a trabajar con tus agentes",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        FilledTonalButton(
            onClick = onCreateClick,
            shape = ShapeTokens.Button
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Crear workspace")
        }
    }
}

@Composable
fun StorageSelector(
    formState: WorkspaceFormState,
    onFormUpdate: (WorkspaceFormState) -> Unit
) {
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Tomar permiso persistente
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)

            // Obtener nombre legible de la carpeta
            val displayName = uri.lastPathSegment?.replace("primary:", "")
                ?.replace(":", "/") ?: uri.toString()

            onFormUpdate(
                formState.copy(
                    externalStorageUri = uri.toString(),
                    externalStorageDisplayName = displayName
                )
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Almacenamiento de archivos",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Opción: Almacenamiento de la app
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = !formState.useExternalStorage,
                    onClick = {
                        onFormUpdate(formState.copy(useExternalStorage = false))
                    },
                    role = Role.RadioButton
                )
                .padding(vertical = 4.dp)
        ) {
            RadioButton(
                selected = !formState.useExternalStorage,
                onClick = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Almacenamiento de la app",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Se borra al desinstalar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Opción: Carpeta del teléfono
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = formState.useExternalStorage,
                    onClick = {
                        onFormUpdate(formState.copy(useExternalStorage = true))
                    },
                    role = Role.RadioButton
                )
                .padding(vertical = 4.dp)
        ) {
            RadioButton(
                selected = formState.useExternalStorage,
                onClick = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Carpeta del teléfono",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Se mantiene al desinstalar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Botón para seleccionar carpeta (solo visible si eligió externo)
        if (formState.useExternalStorage) {
            OutlinedButton(
                onClick = { folderPicker.launch(null) },
                shape = ShapeTokens.Button,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formState.externalStorageDisplayName ?: "Seleccionar carpeta",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWorkspaceDialog(
    formState: WorkspaceFormState,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onFormUpdate: (WorkspaceFormState) -> Unit
) {
    val canCreate = formState.name.isNotBlank() && !isLoading &&
            (!formState.useExternalStorage || formState.externalStorageUri != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ShapeTokens.Dialog,
        title = { Text("Nuevo Workspace") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = formState.name,
                    onValueChange = { onFormUpdate(formState.copy(name = it)) },
                    label = { Text("Nombre") },
                    singleLine = true,
                    shape = ShapeTokens.TextField,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = formState.description,
                    onValueChange = { onFormUpdate(formState.copy(description = it)) },
                    label = { Text("Descripción (opcional)") },
                    maxLines = 2,
                    shape = ShapeTokens.TextField,
                    modifier = Modifier.fillMaxWidth()
                )

                StorageSelector(
                    formState = formState,
                    onFormUpdate = onFormUpdate
                )

                Text(
                    text = "El asistente principal será asignado por defecto. Puedes cambiarlo después entrando al workspace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onCreate,
                enabled = canCreate,
                shape = ShapeTokens.Button
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Crear")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeStorageDialog(
    formState: WorkspaceFormState,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onFormUpdate: (WorkspaceFormState) -> Unit
) {
    val canConfirm = !isLoading &&
            (!formState.useExternalStorage || formState.externalStorageUri != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ShapeTokens.Dialog,
        title = { Text("Cambiar almacenamiento") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StorageSelector(
                    formState = formState,
                    onFormUpdate = onFormUpdate
                )

                Text(
                    text = "Los archivos existentes no se moverán automáticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm,
                enabled = canConfirm,
                shape = ShapeTokens.Button
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Guardar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameWorkspaceDialog(
    formState: WorkspaceFormState,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onFormUpdate: (WorkspaceFormState) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ShapeTokens.Dialog,
        title = { Text("Renombrar Workspace") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = formState.name,
                    onValueChange = { onFormUpdate(formState.copy(name = it)) },
                    label = { Text("Nombre") },
                    singleLine = true,
                    shape = ShapeTokens.TextField,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = formState.description,
                    onValueChange = { onFormUpdate(formState.copy(description = it)) },
                    label = { Text("Descripción (opcional)") },
                    maxLines = 2,
                    shape = ShapeTokens.TextField,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm,
                enabled = formState.name.isNotBlank() && !isLoading,
                shape = ShapeTokens.Button
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Guardar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun DeleteWorkspaceDialog(
    workspace: Workspace,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ShapeTokens.Dialog,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Eliminar workspace") },
        text = {
            Text("¿Estás seguro de que deseas eliminar \"${workspace.name}\"? Se eliminarán todos los mensajes y archivos asociados.")
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = ShapeTokens.Button
            ) {
                Text("Eliminar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
