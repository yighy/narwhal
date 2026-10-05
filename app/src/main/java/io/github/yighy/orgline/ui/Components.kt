package io.github.yighy.orgline.ui

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.TextFieldValue
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
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
import io.github.yighy.orgline.core.NoteTree
import io.github.yighy.orgline.core.SearchQuery
import java.time.LocalDateTime
import io.github.yighy.orgline.core.OrgNote
import io.github.yighy.orgline.core.OrgTime
import io.github.yighy.orgline.data.NoteRepository

/** Title with id: links shown as their description. */
fun displayTitle(title: String): String = IdLinks.plainText(title)

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
    subtitle: (@Composable () -> Unit)?,
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
    /** Key of the element the title moves from when the screen opens (see [sharedText]). */
    sharedTitleKey: String? = null,
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
        // A large flexible app bar whose expanded title (not the collapsed one) moves
        // from the card or row that opened the screen
        TwoRowsTopAppBar(
            title = { expanded ->
                if (expanded) {
                    Text(
                        title,
                        style = MaterialTheme.typography.displaySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = (sharedTitleKey?.let { Modifier.sharedText(it) } ?: Modifier)
                            .then(if (subtitle == null) Modifier.padding(bottom = LargeTitleBottomPadding) else Modifier),
                    )
                } else {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            subtitle = subtitle?.let { content ->
                { expanded ->
                    if (expanded) {
                        Box(Modifier.padding(bottom = LargeTitleBottomPadding)) {
                            // The subtitle moves with the title, so they never overlap
                            Box(sharedTitleKey?.let { Modifier.sharedText(subtitleKey(it)) } ?: Modifier) {
                                ProvideTextStyle(MaterialTheme.typography.titleMedium) { content() }
                            }
                        }
                    } else {
                        content()
                    }
                }
            },
            collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
            expandedHeight = if (subtitle != null) {
                TopAppBarDefaults.LargeFlexibleAppBarWithSubtitleExpandedHeight
            } else {
                TopAppBarDefaults.LargeFlexibleAppBarWithoutSubtitleExpandedHeight
            },
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

/** Key of the subtitle that moves with the title shared as [titleKey]. */
fun subtitleKey(titleKey: String) = "$titleKey/subtitle"

/** Space under the expanded title, as in LargeFlexibleTopAppBar (measured there from the baseline). */
private val LargeTitleBottomPadding = 22.dp

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

/**
 * Text that moves and scales from the element with the same [key] on the previous screen
 * (notebook name into the app bar, note title and content into the note screen).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedText(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalAnimatedVisibilityScope.current ?: return this
    val motion = MaterialTheme.motionScheme
    return with(shared) {
        this@sharedText.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> motion.slowSpatialSpec() },
            // Laid out once at its own size, then scaled: text keeps its lines while it grows
            resizeMode = scaleToBounds(ContentScale.FillWidth, Alignment.TopStart),
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
    // Orgzly-style query; the selected tag chip adds a t. condition
    val queryText = listOfNotNull(viewModel.query, viewModel.tag?.let { "t.\"$it\"" }).joinToString(" ")
    val context = remember(library) { searchContext(library) }
    val results = remember(queryText, notes, context) {
        if (idle) emptyList() else SearchQuery.parse(queryText).apply(notes, context)
    }
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
                        if (idle) "Type words, or a query like  i.todo t.work s.today  o.p" else "No matching notes",
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
                    onOpenId = viewModel::openById,
                )
            }
        }
    }
}

/** State, priority, title and a details line (tags, planning, [subtitle]). */
@Composable
fun NoteLine(
    note: OrgNote,
    subtitle: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onOpenId: ((String) -> Unit)? = null,
    titleModifier: Modifier = Modifier,
) {
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
            val titleStyle = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (note.isDone) TextDecoration.LineThrough else null,
                color = if (note.isDone) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
            if (onOpenId != null) {
                LinkedText(note.title, titleStyle, onOpenId, titleModifier, maxLines = 2)
            } else {
                Text(displayTitle(note.title), style = titleStyle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = titleModifier)
            }
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

/**
 * Notebook's [icon] (an emoji), or else its initial, inside an expressive shape picked from
 * the notebook's name.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotebookAvatar(title: String, icon: String? = null) {
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
        if (icon != null) {
            Text(icon, fontSize = 28.sp)
        } else {
            Text(
                title.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = content,
            )
        }
    }
}

/**
 * Text where [[id:...]] links are shown by their description and open the target note.
 * A tap elsewhere calls [onTextTap] with the position tapped in [text], to edit it from there.
 */
@Composable
fun LinkedText(
    text: String,
    style: TextStyle,
    onOpenId: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    onTextTap: ((Int) -> Unit)? = null,
) {
    val linkStyle = TextLinkStyles(
        SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    // The text as shown, and the shown and raw range of each link
    val (annotated, links) = remember(text, linkStyle) {
        val ranges = mutableListOf<Pair<IntRange, IntRange>>()
        val shown = buildAnnotatedString {
            var last = 0
            IdLinks.find(text).forEach { link ->
                append(text.substring(last, link.range.first))
                val start = length
                withLink(LinkAnnotation.Clickable(link.id, linkStyle) { onOpenId(link.id) }) {
                    append(link.description ?: link.id)
                }
                ranges += (start until length) to link.range
                last = link.range.last + 1
            }
            append(text.substring(last))
        }
        shown to ranges
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val tap = if (onTextTap == null) Modifier else Modifier.pointerInput(annotated) {
        detectTapGestures { position ->
            val shown = layout?.getOffsetForPosition(position) ?: annotated.length
            rawOffset(shown, links)?.let(onTextTap)
        }
    }
    Text(
        annotated,
        style = style,
        modifier = modifier.then(tap),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
    )
}

/** Position in the raw text of position [shown] in the text as displayed; null on a link. */
private fun rawOffset(shown: Int, links: List<Pair<IntRange, IntRange>>): Int? {
    var shift = 0
    for ((displayed, raw) in links) {
        if (shown < displayed.first) break
        // A tap on a link opens it (the link handles that)
        if (shown <= displayed.last) return null
        shift = raw.last - displayed.last
    }
    return shown + shift
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

/**
 * Text field that looks like the text it edits (same [style], no box or label), focused when
 * shown, with the cursor where [value] has it. The caller saves on [onFocusLost], and on [onDone]
 * (the keyboard's Done key) if given. [placeholder] shows while it is empty.
 */
@Composable
fun InPlaceField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    onFocusLost: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val textStyle = style.copy(color = style.color.takeOrElse { LocalContentColor.current })
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = if (onDone != null) KeyboardOptions(imeAction = ImeAction.Done) else KeyboardOptions.Default,
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier
            .focusRequester(focus)
            .onFocusChanged {
                // Only after the field had the focus, not on the first composition
                if (focused && !it.isFocused) onFocusLost?.invoke()
                focused = it.isFocused
            },
        decorationBox = { field ->
            Box {
                if (value.text.isEmpty() && placeholder != null) {
                    Text(placeholder, style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                }
                field()
            }
        },
    )
}

/**
 * Fades the start and end of a scrolling row where there is more to see, so it shows it scrolls.
 */
fun Modifier.fadingEdges(state: LazyListState, width: Dp = 32.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = width.toPx().coerceAtMost(size.width / 2)
        if (state.canScrollBackward) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = w),
                size = Size(w, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (state.canScrollForward) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - w, endX = size.width),
                topLeft = Offset(size.width - w, 0f),
                size = Size(w, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * Clears the focus when the keyboard is closed (back, or its own close key), so the field being
 * edited is saved and no cursor is left behind.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClearFocusWhenKeyboardHides() {
    val imeVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current
    var wasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            wasVisible = true
        } else if (wasVisible) {
            // Moving from one field to another can hide the keyboard for a moment
            delay(300)
            wasVisible = false
            focusManager.clearFocus()
        }
    }
}

/** Notebook names, keywords and inherited tags that search queries need. */
fun searchContext(library: NoteRepository.Library): SearchQuery.Context {
    val inherited = library.files.associate { it.uri to NoteTree.inheritedTags(it.notes) }
    return SearchQuery.Context(
        now = LocalDateTime.now(),
        keywords = { note -> library.file(note.fileUri)?.doc?.keywords ?: io.github.yighy.orgline.core.Keywords.DEFAULT },
        // Orgzly's notebook name: the file name without .org
        notebookName = { note -> note.fileName.removeSuffix(".org") },
        inheritedTags = { note -> inherited[note.fileUri]?.getOrNull(note.index).orEmpty() },
    )
}

/**
 * "12 notes · 3 to do" under a title; tapping "3 to do" runs [onTodoClick]
 * (a search for the notes to do).
 */
@Composable
fun CountsSubtitle(notes: Int?, todo: Int, onTodoClick: () -> Unit) {
    Row {
        notes?.let {
            Text(if (it == 1) "1 note" else "$it notes", maxLines = 1)
            if (todo > 0) Text(" · ", maxLines = 1)
        }
        if (todo > 0) {
            Text(
                "$todo to do",
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.clickable(onClick = onTodoClick),
            )
        }
    }
}
