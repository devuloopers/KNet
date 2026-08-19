package com.devuloopers.knet.storage.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.devuloopers.knet.storage.apistudio.dao.CollectionDao
import com.devuloopers.knet.storage.apistudio.entity.CollectionEntity
import com.devuloopers.knet.storage.apistudio.entity.CollectionFolderEntity
import com.devuloopers.knet.storage.apistudio.entity.SavedRequestEntity
import com.devuloopers.knet.storage.capture.dao.CanonicalCaptureDao
import com.devuloopers.knet.storage.capture.entity.BodyObjectEntity
import com.devuloopers.knet.storage.capture.entity.CanonicalExchangeEntity
import com.devuloopers.knet.storage.capture.entity.CaptureGapEntity
import com.devuloopers.knet.storage.capture.entity.CaptureSessionEntity
import com.devuloopers.knet.storage.capture.entity.DeletionOutboxEntity
import com.devuloopers.knet.storage.capture.entity.DuplexMessageEntity
import com.devuloopers.knet.storage.capture.entity.InspectionAnnotationEntity
import com.devuloopers.knet.storage.capture.entity.TrafficConnectionEntity
import com.devuloopers.knet.storage.device.dao.RegisteredDeviceDao
import com.devuloopers.knet.storage.device.entity.PairingInvitationEntity
import com.devuloopers.knet.storage.device.entity.RegisteredDeviceEntity
import com.devuloopers.knet.storage.device.entity.TrustedDeviceCredentialEntity
import com.devuloopers.knet.storage.rules.dao.BreakpointRuleDao
import com.devuloopers.knet.storage.rules.entity.BreakpointRuleEntity

/**
 * Room Database contract definition for KNet JVM Desktop persistence.
 */
@Database(
    entities = [
        CollectionEntity::class,
        CollectionFolderEntity::class,
        SavedRequestEntity::class,
        BreakpointRuleEntity::class,
        CaptureSessionEntity::class,
        TrafficConnectionEntity::class,
        CanonicalExchangeEntity::class,
        BodyObjectEntity::class,
        DuplexMessageEntity::class,
        InspectionAnnotationEntity::class,
        CaptureGapEntity::class,
        DeletionOutboxEntity::class,
        RegisteredDeviceEntity::class,
        TrustedDeviceCredentialEntity::class,
        PairingInvitationEntity::class,
    ],
    version = 15,
    autoMigrations = [AutoMigration(from = 14, to = 15)],
)
abstract class KNetDatabase : RoomDatabase() {

    abstract fun collectionDao(): CollectionDao

    abstract fun breakpointRuleDao(): BreakpointRuleDao

    /** Canonical session, connection, exchange, body, gap, and deletion-outbox persistence. */
    abstract fun canonicalCaptureDao(): CanonicalCaptureDao

    /** Durable registered identity, trusted credentials, and pending pairing invitations. */
    abstract fun registeredDeviceDao(): RegisteredDeviceDao
}
