/*
 * Copyright (C) 2015 Mantas Varnagiris.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.mvcoding.expensius.feature.tag

import com.mvcoding.expensius.RxSchedulers
import com.mvcoding.expensius.feature.ItemsView
import com.mvcoding.expensius.feature.LoadingView
import com.mvcoding.expensius.feature.ModelDisplayType
import com.mvcoding.expensius.model.Order
import com.mvcoding.expensius.model.Tag
import com.mvcoding.expensius.service.TagsService
import com.mvcoding.expensius.service.TagsWriteService
import com.mvcoding.mvp.Presenter
import rx.Observable
import java.lang.Math.max
import java.lang.Math.min

class TagsPresenter(
        private val modelDisplayType: ModelDisplayType,
        private val tagsService: TagsService,
        private val tagsWriteService: TagsWriteService,
        private val schedulers: RxSchedulers) : Presenter<TagsPresenter.View>() {

    override fun onViewAttached(view: View) {
        super.onViewAttached(view)

        view.showModelDisplayType(modelDisplayType)
        view.showLoading()
        tagsService.items()
                .first()
                .subscribeOn(schedulers.io)
                .observeOn(schedulers.main)
                .doOnNext { view.hideLoading() }
                .subscribeUntilDetached { view.showItems(it) }

        tagsService.addedItems()
                .subscribeOn(schedulers.io)
                .observeOn(schedulers.main)
                .subscribeUntilDetached { view.showAddedItems(it.position, it.items) }

        tagsService.changedItems()
                .subscribeOn(schedulers.io)
                .observeOn(schedulers.main)
                .subscribeUntilDetached { view.showChangedItems(it.position, it.items) }

        tagsService.removedItems()
                .subscribeOn(schedulers.io)
                .observeOn(schedulers.main)
                .subscribeUntilDetached { view.showRemovedItems(it.position, it.items) }

        tagsService.movedItems()
                .subscribeOn(schedulers.io)
                .observeOn(schedulers.main)
                .subscribeUntilDetached { view.showMovedItem(it.fromPosition, it.toPosition, it.item) }

        view.tagMoves()
                .subscribeOn(schedulers.main)
                .observeOn(schedulers.io)
                .withLatestFrom(tagsService.items(), { tagMove, tags -> reorderTags(tagMove, tags) })
                .switchMap { tagsWriteService.updateTags(it.toSet()) }
                .subscribeUntilDetached { }

        //        unsubscribeOnDetach(merge(view.onTagSelected(), view.onCreateTag().map { /*Tag()*/ noTag }).subscribe { view.displayTagEdit(it) })
        view.archivedTagsRequests().subscribeUntilDetached { view.displayArchivedTags() }
    }

    private fun reorderTags(tagMove: TagMove, tags: List<Tag>): List<Tag> {
        val fromPosition = tagMove.fromPosition
        val toPosition = tagMove.toPosition
        val minPosition = min(fromPosition, toPosition)
        val maxPosition = max(fromPosition, toPosition)
        return tags.mapIndexed { position, tag ->
            when {
                position == fromPosition -> tag.withOrder(Order(toPosition))
                position >= minPosition && position <= maxPosition -> tag.withOrder(Order(position + if (fromPosition > toPosition) 1 else -1))
                else -> tag.withOrder(Order(position))
            }
        }
    }

    data class TagMove(val fromPosition: Int, val toPosition: Int)

    interface View : Presenter.View, ItemsView<Tag>, LoadingView {
        fun archivedTagsRequests(): Observable<Unit>
        fun tagMoves(): Observable<TagMove>

        fun showModelDisplayType(modelDisplayType: ModelDisplayType)

        //        fun onTagSelected(): Observable<Tag>
        //        fun onCreateTag(): Observable<Unit>
        //        fun displayTagEdit(tag: Tag)

        fun displayArchivedTags()
    }
}
