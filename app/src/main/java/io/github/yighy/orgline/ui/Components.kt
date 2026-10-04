package io.github.yighy.orgline.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import io.github.yighy.orgline.core.IdLinks
import io.github.yighy.orgline.core.NoteSearch
import io.github.yighy.orgline.core.OrgNote
import io.github.yighy.orgline.core.OrgTime
import io.github.yighy.orgline.data.NoteRepository

/** Title with id: links shown as their description. */
fun displayTitle(title: String): String {
    val links = IdLinks.find(title)
    if (links.isEmpty()) return title
    return buildString {
        var last = 0
        links.forEach { link ->
            append(title, last, link.range.first)
            append(link.description ?: link.id)
            last = link.range.last + 1
        }
        append(title, last, title.length)
    }
}

/** App bar colors matching the background, so the bar blends with the screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun blendedTopBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surface,
    scrolledContainerColor = MaterialTheme.colorScheme.surface,
)

/**
 * Large app bar that collapses on scroll; the search icon turns it into a search field.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchableTopBar(
    title: String,
    subtitle: String?,
    navigationIcon: ImageVector,
    navigationDescription: String,
    onNavigation: () -> Unit,
    searchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    scrollBehavior: TopAppBarScrollBehavior,
    extraActions: @Composable RowScope.() -> Unit = {},
    menu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }

    if (searchActive) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        TopAppBar(
            colors = blendedTopBarColors(),
            navigationIcon = {
                IconButton(onClick = { onSearchActiveChange(false) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
                }
            },
            title = {
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            searchPlaceholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            },
            actions = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Clear, "Clear search") }
                }
            },
        )
    } else {
        LargeFlexibleTopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            subtitle = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            navigationIcon = {
                IconButton(onClick = onNavigation) { Icon(navigationIcon, navigationDescription) }
            },
            actions = {
                extraActions()
                IconButton(onClick = { onSearchActiveChange(true) }) { Icon(Icons.Default.Search, "Search") }
                if (menu != null) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            menu { menuOpen = false }
                        }
                    }
                }
            },
            colors = blendedTopBarColors(),
            scrollBehavior = scrollBehavior,
        )
    }
}

/** Shared transition scopes, provided by the screen host so screens can share elements. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Container transform: an element with the same [key] on the next screen grows out of this one
 * (notebook card into notebook screen, note row into note screen).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedContainer(key: String, shape: Shape): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalAnimatedVisibilityScope.current ?: return this
    val motion = MaterialTheme.motionScheme
    return with(shared) {
        this@sharedContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> motion.slowSpatialSpec() },
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/** Search results with a tag filter, over [notes]. */
@Composable
fun SearchResults(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    notes: List<OrgNote>,
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    val idle = viewModel.query.isBlank() && viewModel.tag == null
    val results = if (idle) emptyList() else notes.filter { NoteSearch.matches(it, viewModel.query, viewModel.tag) }
    Column(modifier) {
        if (tags.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tags) { tag ->
                    FilterChip(
                        selected = viewModel.tag == tag,
                        onClick = { viewModel.tag = if (viewModel.tag == tag) null else tag },
                        label = { Text(tag) },
                    )
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 112.dp)) {
            if (results.isEmpty()) {
                item {
                    Text(
                        if (idle) "Type to search titles, content and tags, or pick a tag" else "No matching notes",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(results, key = { it.fileUri + "#" + it.index }) { note ->
                NoteLine(
                    note = note,
                    subtitle = library.file(note.fileUri)?.title,
                    onClick = { viewModel.openNote(note) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** State, priority, title and a details line (tags, planning, [subtitle]). */
@Composable
fun NoteLine(note: OrgNote, subtitle: String?, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column((if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).then(modifier)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            note.state?.let {
                StateLabel(it, note.isDone)
                Spacer(Modifier.width(8.dp))
            }
            note.priority?.let {
                Text("#$it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                displayTitle(note.title),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (note.isDone) TextDecoration.LineThrough else null,
                color = if (note.isDone) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
        }
        val details = buildList {
            if (note.tags.isNotEmpty()) add(note.tags.joinToString(" ") { "#$it" })
            note.scheduled?.let { add("Scheduled " + OrgTime.display(it)) }
            note.deadline?.let { add("Deadline " + OrgTime.display(it)) }
            subtitle?.let { add(it) }
        }
        if (details.isNotEmpty()) {
            Text(
                details.joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StateLabel(state: String, isDone: Boolean) {
    val container = if (isDone) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (isDone) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onTertiaryContainer
    Text(
        state,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = content,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Notebook initial inside an expressive shape, picked from the notebook's name. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotebookAvatar(title: String) {
    val shapes = listOf(
        MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny,
        MaterialShapes.Gem, MaterialShapes.Pentagon, MaterialShapes.Cookie6Sided,
        MaterialShapes.SoftBurst, MaterialShapes.Puffy,
    )
    val colors = MaterialTheme.colorScheme.let {
        listOf(
            it.primaryContainer to it.onPrimaryContainer,
            it.secondaryContainer to it.onSecondaryContainer,
            it.tertiaryContainer to it.onTertiaryContainer,
        )
    }
    val hash = title.lowercase().hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
    val (container, content) = colors[hash % colors.size]
    Box(
        Modifier
            .size(56.dp)
            .clip(shapes[hash % shapes.size].toShape())
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = content,
        )
    }
}

/** Text where [[id:...]] links are shown by their description and open the target note. */
@Composable
fun LinkedText(text: String, style: TextStyle, onOpenId: (String) -> Unit, modifier: Modifier = Modifier) {
    val linkStyle = TextLinkStyles(
        SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    val annotated: AnnotatedString = remember(text, linkStyle) {
        buildAnnotatedString {
            var last = 0
            IdLinks.find(text).forEach { link ->
                append(text.substring(last, link.range.first))
                withLink(LinkAnnotation.Clickable(link.id, linkStyle) { onOpenId(link.id) }) {
                    append(link.description ?: link.id)
                }
                last = link.range.last + 1
            }
            append(text.substring(last))
        }
    }
    Text(annotated, style = style, modifier = modifier)
}

/** Pull down to reload the files from the folder. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PullToRefresh(viewModel: AppViewModel, content: @Composable () -> Unit) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = viewModel.refreshing,
        onRefresh = viewModel::pullToRefresh,
        state = state,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = viewModel.refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        content()
    }
}

/** Text field with cancel / save buttons, focused when shown. */
@Composable
fun DraftField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    singleLine: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    minLines: Int = 1,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.padding(vertical = 8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = singleLine,
            minLines = minLines,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel") }
            IconButton(onClick = onSave) { Icon(Icons.Default.Check, "Save") }
        }
    }
}
