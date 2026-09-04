package xyz.lilsus.raylsuite.feature.paymenthub.render

import androidx.compose.runtime.Immutable
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHub
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.CanvasLayout
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.CanvasTileSize
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.HubGrid
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.HubGridSpan

/**
 * One tile on the canvas. A leaf pays; a container only surfaces the leaves it holds, and the
 * absence of an amount line is what tells the user it will never move money itself.
 */
@Immutable
data class HubTileRenderModel(
    val id: HubItemId,
    val label: String,
    val mark: HubMark,
    /** Lightning address for a leaf; a container has none. */
    val subtitle: String?,
    val amountLine: HubAmountLine?,
    /** Rendered span, which follows the member list once a container is open. */
    val columns: Int,
    val rows: Int,
    val storedSize: CanvasTileSize,
    val isContainer: Boolean,
    val memberCount: Int,
    val showsMembers: Boolean,
    /** Stored at its own open size, so it has no collapsed state and no expand affordance. */
    val permanentlyOpen: Boolean,
    val members: List<HubTileMemberRenderModel>
) {
    val span: HubGridSpan
        get() = HubGridSpan(columns, rows)

    /** A container that can still be opened by tapping it. */
    val expandable: Boolean
        get() = isContainer && !permanentlyOpen
}

@Immutable
data class HubTileMemberRenderModel(
    val id: HubItemId,
    val label: String,
    val mark: HubMark,
    val amountLine: HubAmountLine
)

/**
 * Projects the hub document and the stored arrangement into tiles. Open size is derived here and
 * never stored, so a container shrunk to one column can never keep drawing full-width member rows.
 */
fun PaymentHub.toCanvasTiles(
    layout: CanvasLayout,
    expandedId: HubItemId? = null
): List<HubTileRenderModel> = layout.tiles.mapNotNull { tile ->
    val target = target(tile.id)
    if (target != null) {
        return@mapNotNull HubTileRenderModel(
            id = target.id,
            label = target.title,
            mark = target.mark(),
            subtitle = target.address.full,
            amountLine = target.amountLine(),
            columns = tile.size.columns,
            rows = tile.size.rows,
            storedSize = tile.size,
            isContainer = false,
            memberCount = 0,
            showsMembers = false,
            permanentlyOpen = false,
            members = emptyList()
        )
    }
    val group = group(tile.id) ?: return@mapNotNull null
    val members = members(group.id)
    val openRows = openRowsFor(members.size)
    val permanentlyOpen = tile.size.columns >= HubGrid.COLUMNS && tile.size.rows >= openRows
    val showsMembers = permanentlyOpen || expandedId == group.id
    HubTileRenderModel(
        id = group.id,
        label = group.title,
        mark = HubMark(hubInitials(group.title), group.appearance.icon, group.appearance.accent),
        subtitle = null,
        amountLine = null,
        columns = if (showsMembers) HubGrid.COLUMNS else tile.size.columns,
        rows = if (showsMembers) openRows else tile.size.rows,
        storedSize = tile.size,
        isContainer = true,
        memberCount = members.size,
        showsMembers = showsMembers,
        permanentlyOpen = permanentlyOpen,
        members =
            members.map { member ->
                HubTileMemberRenderModel(
                    id = member.id,
                    label = member.title,
                    mark = member.mark(),
                    amountLine = member.amountLine()
                )
            }
    )
}

/** Two members open side by side in one row; three or more need the list. */
fun openRowsFor(memberCount: Int): Int = if (memberCount <= 2) 1 else 2

/**
 * Closed sizes a tile may take. A container may not be stored larger than it opens, because the
 * extra row would only ever sit empty.
 */
fun allowedTileSizes(isContainer: Boolean, memberCount: Int): List<CanvasTileSize> =
    CanvasTileSize.entries.filter { size ->
        !isContainer || size.rows <= openRowsFor(memberCount)
    }
