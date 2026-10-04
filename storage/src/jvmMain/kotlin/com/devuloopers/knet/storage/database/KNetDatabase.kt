package com.devuloopers.knet.storage.database

import androidx.room.AutoMigration
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import com.devuloopers.knet.storage.apistudio.dao.CollectionDao
import com.devuloopers.knet.storage.apistudio.dao.ProtocolDocumentDao
import com.devuloopers.knet.storage.apistudio.entity.CollectionEntity
import com.devuloopers.knet.storage.apistudio.entity.CollectionFolderEntity
import com.devuloopers.knet.storage.apistudio.entity.SavedRequestEntity
import com.devuloopers.knet.storage.apistudio.entity.ApiStudioWorkspaceDocumentEntity
import com.devuloopers.knet.storage.apistudio.entity.ApiStudioProtocolSchemaEntity
import com.devuloopers.knet.storage.capture.dao.CanonicalCaptureDao
import com.devuloopers.knet.storage.capture.entity.BodyObjectEntity
import com.devuloopers.knet.storage.capture.entity.CanonicalExchangeEntity
import com.devuloopers.knet.storage.capture.entity.CaptureGapEntity
import com.devuloopers.knet.storage.capture.entity.CaptureSessionEntity
import com.devuloopers.knet.storage.capture.entity.DeletionOutboxEntity
import com.devuloopers.knet.storage.capture.entity.DuplexMessageEntity
import com.devuloopers.knet.storage.capture.entity.InspectionAnnotationEntity
import com.devuloopers.knet.storage.capture.entity.OpaqueFlowEntity
import com.devuloopers.knet.storage.capture.entity.TrafficConnectionEntity
import com.devuloopers.knet.storage.device.dao.RegisteredDeviceDao
import com.devuloopers.knet.storage.device.entity.PairingInvitationEntity
import com.devuloopers.knet.storage.device.entity.RegisteredDeviceEntity
import com.devuloopers.knet.storage.device.entity.TrustedDeviceCredentialEntity
import com.devuloopers.knet.storage.rules.dao.BreakpointRuleDao
import com.devuloopers.knet.storage.rules.entity.BreakpointRuleEntity
import com.devuloopers.knet.storage.networkconditions.dao.NetworkConditionDao
import com.devuloopers.knet.storage.networkconditions.entity.NetworkConditionProfileEntity
import com.devuloopers.knet.storage.networkconditions.entity.NetworkConditionRuleEntity
import com.devuloopers.knet.storage.networkconditions.entity.NetworkConditionSettingsEntity
import com.devuloopers.knet.storage.protectedtraffic.dao.ProtectedTrafficDao
import com.devuloopers.knet.storage.protectedtraffic.entity.ProtectedServiceGroupEntity
import com.devuloopers.knet.storage.protectedtraffic.entity.ProtectedTrafficRuleEntity
import com.devuloopers.knet.storage.protectedtraffic.entity.ProtectedTrafficSettingsEntity

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
        OpaqueFlowEntity::class,
        BodyObjectEntity::class,
        DuplexMessageEntity::class,
        InspectionAnnotationEntity::class,
        CaptureGapEntity::class,
        DeletionOutboxEntity::class,
        RegisteredDeviceEntity::class,
        TrustedDeviceCredentialEntity::class,
        PairingInvitationEntity::class,
        ApiStudioWorkspaceDocumentEntity::class,
        ApiStudioProtocolSchemaEntity::class,
        NetworkConditionSettingsEntity::class,
        NetworkConditionProfileEntity::class,
        NetworkConditionRuleEntity::class,
        ProtectedTrafficSettingsEntity::class,
        ProtectedTrafficRuleEntity::class,
        ProtectedServiceGroupEntity::class,
    ],
    version = 32,
    autoMigrations = [
        AutoMigration(from = 13, to = 14, spec = Migration13To14::class),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 21, to = 22),
        AutoMigration(from = 23, to = 24),
        AutoMigration(from = 24, to = 25, spec = Migration24To25::class),
        AutoMigration(from = 25, to = 26),
        AutoMigration(from = 26, to = 27),
        AutoMigration(from = 27, to = 28),
        AutoMigration(from = 28, to = 29),
        AutoMigration(from = 29, to = 30),
        AutoMigration(from = 30, to = 31),
        AutoMigration(from = 31, to = 32),
    ],
)
abstract class KNetDatabase : RoomDatabase() {

    abstract fun collectionDao(): CollectionDao

    /** Opaque authored protocol documents and imported schema sources. */
    abstract fun protocolDocumentDao(): ProtocolDocumentDao

    abstract fun breakpointRuleDao(): BreakpointRuleDao

    /** Canonical session, connection, exchange, body, gap, and deletion-outbox persistence. */
    abstract fun canonicalCaptureDao(): CanonicalCaptureDao

    /** Durable registered identity, trusted credentials, and pending pairing invitations. */
    abstract fun registeredDeviceDao(): RegisteredDeviceDao

    /** Durable global state, custom profiles, and normalized domain rules. */
    abstract fun networkConditionDao(): NetworkConditionDao

    /** Durable protected-traffic fallback, user-rule, and compatibility-group persistence. */
    abstract fun protectedTrafficDao(): ProtectedTrafficDao
}

@DeleteColumn(tableName = "saved_requests", columnName = "customMethod")
internal class Migration13To14 : AutoMigrationSpec

@DeleteTable(tableName = "api_studio_protocol_documents")
internal class Migration24To25 : AutoMigrationSpec
