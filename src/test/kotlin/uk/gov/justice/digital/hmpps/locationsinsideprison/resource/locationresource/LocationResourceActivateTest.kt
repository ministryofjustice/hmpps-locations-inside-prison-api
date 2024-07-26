package uk.gov.justice.digital.hmpps.locationsinsideprison.resource.locationresource

import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser

const val EXPECTED_USERNAME_ACTIVATE = "A_TEST_USER"

@WithMockAuthUser(username = EXPECTED_USERNAME_ACTIVATE)
class LocationResourceActivateTest : CommonDataTestBase()
