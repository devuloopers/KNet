package com.devuloopers.knet.ui.desktop.certificate

import com.devuloopers.knet.engine.certificate.CertificateManager
import com.devuloopers.knet.engine.certificate.CertificateManagerImpl
import com.devuloopers.knet.ui.desktop.certificate.di.certificateUiModule
import com.devuloopers.knet.ui.desktop.certificate.model.CaDetails
import com.devuloopers.knet.ui.desktop.certificate.model.CaStatus
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateIntent
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateState
import com.devuloopers.knet.ui.desktop.certificate.model.ClientCertificate
import com.devuloopers.knet.ui.desktop.certificate.model.MtlsRule
import com.devuloopers.knet.ui.desktop.certificate.model.TrustInstallationState
import com.devuloopers.knet.ui.desktop.certificate.viewmodel.CertificateViewModel
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Migration regression tests for the `:ui:desktop:certificate` module.
 *
 * This test guarantees core classes and dependency modules are preserved and loadable.
 */
public class MigrationRegressionTest {

    /**
     * Verifies that the migrated presentation and state classes exist and are available.
     *
     * Design Intent: Prevents missing classes or package mismatch during refactoring of features.
     */
    @Test
    public fun testMigrationClassesExist() {
        assertNotNull(CertificateViewModel::class)
        assertNotNull(CertificateState::class)
        assertNotNull(CaStatus::class)
        assertNotNull(CaDetails::class)
        assertNotNull(ClientCertificate::class)
        assertNotNull(MtlsRule::class)
        assertNotNull(TrustInstallationState::class)
        assertNotNull(CertificateIntent::class)
        assertNotNull(CertificateManager::class)
        assertNotNull(CertificateManagerImpl::class)
    }

    /**
     * Verifies that the dependency injection module configuration object is available.
     *
     * Design Intent: Assures Koin module descriptors can be integrated into the parent application scope.
     */
    @Test
    public fun testDiModuleExists() {
        assertNotNull(certificateUiModule)
    }
}
