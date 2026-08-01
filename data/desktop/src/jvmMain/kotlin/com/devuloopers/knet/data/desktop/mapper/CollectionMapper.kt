package com.devuloopers.knet.data.desktop.mapper

import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.storage.apistudio.entity.CollectionEntity
import com.devuloopers.knet.storage.apistudio.entity.CollectionFolderEntity

/**
 * Maps between SQLite Room collection entities and Domain collection models.
 */
public object CollectionMapper {

    public fun mapEntityToDomain(
        entity: CollectionEntity,
        folders: List<CollectionFolderEntity> = emptyList()
    ): ApiCollection {
        return ApiCollection(
            id = entity.id,
            name = entity.name,
            folders = folders.map { mapFolderEntityToDomain(it) }
        )
    }

    public fun mapFolderEntityToDomain(entity: CollectionFolderEntity): CollectionFolder {
        return CollectionFolder(
            id = entity.id,
            name = entity.name,
            isExpanded = entity.isExpanded
        )
    }

    public fun mapDomainToEntity(collection: ApiCollection): CollectionEntity {
        return CollectionEntity(
            id = collection.id,
            name = collection.name
        )
    }
}
