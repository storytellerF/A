package com.storyteller_f.a.cloud.panel.common

import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.storyteller_f.a.api.core.PaginationQuery
import com.storyteller_f.a.app.core.common.CachedLoadingHandler
import com.storyteller_f.a.app.core.common.CompatPagingSource
import com.storyteller_f.a.app.core.common.CustomRemoteMediator
import com.storyteller_f.a.app.core.common.IntKeyConverter
import com.storyteller_f.a.app.core.common.PagingViewModel
import com.storyteller_f.a.app.core.common.RegularPagingSource
import com.storyteller_f.a.app.core.common.SimpleViewModel
import com.storyteller_f.a.client.core.LoadingHandler
import com.storyteller_f.a.client.core.PanelSessionManager
import com.storyteller_f.a.client.core.getAllUsers
import com.storyteller_f.a.client.core.overview
import com.storyteller_f.shared.model.PanelOverview
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.UserCollection
import com.storyteller_f.storage.getName
import kotlinx.coroutines.flow.Flow

class AllUsersViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
) : PagingViewModel<UserInfo>() {
    val modelCollection = UserCollection.AllUsers

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<UserInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection.getName(),
            RegularPagingSource { key, size ->
                sessionManager.getAllUsers(PaginationQuery(key, size = size))
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.user.clean(modelCollection)
            }
            data.forEach {
                modelStorage.user.save(modelCollection, it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.user.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

class OverviewViewModel(sessionManager: PanelSessionManager, modelStorage: ModelStorage) :
    SimpleViewModel<PanelOverview>() {
    override val handler: LoadingHandler<PanelOverview> = CachedLoadingHandler(
        modelStorage.overview.observeDatum(),
        viewModelScope,
        {
            modelStorage.overview.save(it)
        }
    ) {
        sessionManager.overview()
    }
}
