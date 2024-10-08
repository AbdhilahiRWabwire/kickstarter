package com.kickstarter.viewmodels

import android.util.Pair
import com.kickstarter.KSRobolectricTestCase
import com.kickstarter.libs.Environment
import com.kickstarter.libs.MockCurrentUserV2
import com.kickstarter.libs.utils.EventName
import com.kickstarter.mock.factories.BackingFactory
import com.kickstarter.mock.factories.ProjectDataFactory
import com.kickstarter.mock.factories.ProjectFactory
import com.kickstarter.mock.factories.RewardFactory
import com.kickstarter.mock.factories.ShippingRuleFactory
import com.kickstarter.mock.factories.StoredCardFactory
import com.kickstarter.mock.factories.UserFactory
import com.kickstarter.mock.services.MockApolloClientV2
import com.kickstarter.models.Backing
import com.kickstarter.models.CheckoutPayment
import com.kickstarter.models.CreatePaymentIntentInput
import com.kickstarter.models.PaymentValidationResponse
import com.kickstarter.models.Project
import com.kickstarter.models.StoredCard
import com.kickstarter.models.UserPrivacy
import com.kickstarter.services.mutations.CreateCheckoutData
import com.kickstarter.ui.data.PledgeData
import com.kickstarter.ui.data.PledgeFlowContext
import com.kickstarter.viewmodels.projectpage.LatePledgeCheckoutUIState
import com.kickstarter.viewmodels.projectpage.LatePledgeCheckoutViewModel
import io.reactivex.Observable
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LatePledgeCheckoutViewModelTest : KSRobolectricTestCase() {

    private lateinit var viewModel: LatePledgeCheckoutViewModel

    private fun setUpEnvironment(environment: Environment) {
        viewModel = LatePledgeCheckoutViewModel.Factory(environment).create(LatePledgeCheckoutViewModel::class.java)
    }

    @Test
    fun `test_when_loading_called_then_state_shows_loading`() = runTest {
        setUpEnvironment(environment())

        val state = mutableListOf<LatePledgeCheckoutUIState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.latePledgeCheckoutUIState.toList(state)
        }

        viewModel.loading()
        assertEquals(
            state.last(),
            LatePledgeCheckoutUIState(
                isLoading = true
            )
        )
    }

    @Test
    fun `test_when_user_logged_in_then_email_is_provided`() = runTest {
        val user = UserFactory.user()
        val currentUserV2 = MockCurrentUserV2(initialUser = user)

        val environment = environment().toBuilder()
            .apolloClientV2(object : MockApolloClientV2() {
                override fun getStoredCards(): Observable<List<StoredCard>> {
                    return Observable.empty()
                }
            })
            .currentUserV2(currentUserV2)
            .build()

        setUpEnvironment(environment)

        val state = mutableListOf<LatePledgeCheckoutUIState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.latePledgeCheckoutUIState.toList(state)
        }

        assertEquals(
            state.last(),
            LatePledgeCheckoutUIState(
                userEmail = "some@email.com"
            )
        )
    }

    @Test
    fun `test_when_user_logged_in_then_cards_are_fetched`() = runTest {
        val user = UserFactory.user()
        val currentUserV2 = MockCurrentUserV2(initialUser = user)
        val cardList = listOf(StoredCardFactory.visa())

        val environment = environment().toBuilder()
            .apolloClientV2(object : MockApolloClientV2() {
                override fun getStoredCards(): Observable<List<StoredCard>> {
                    return Observable.just(cardList)
                }
            })
            .currentUserV2(currentUserV2)
            .build()

        setUpEnvironment(environment)

        val state = mutableListOf<LatePledgeCheckoutUIState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.latePledgeCheckoutUIState.toList(state)
        }

        assertEquals(
            state.last(),
            LatePledgeCheckoutUIState(
                storeCards = cardList,
                userEmail = "some@email.com"
            )
        )
    }

    @Test
    fun `test_when_user_clicks_add_new_card_then_setup_intent_is_called`() = runTest {
        val user = UserFactory.user()
        val currentUserV2 = MockCurrentUserV2(initialUser = user)

        val environment = environment().toBuilder()
            .apolloClientV2(object : MockApolloClientV2() {
                override fun createSetupIntent(project: Project?): Observable<String> {
                    return Observable.just("thisIsAClientSecret")
                }
            })
            .currentUserV2(currentUserV2)
            .build()

        setUpEnvironment(environment)

        val state = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.clientSecretForNewPaymentMethod.toList(state)
        }

        viewModel.onAddNewCardClicked(Project.builder().build())

        assertEquals(
            state.last(),
            "thisIsAClientSecret"
        )
    }

    @Test
    fun `test_when_new_card_added_then_payment_methods_are_refreshed`() = runTest {
        val user = UserFactory.user()
        val currentUserV2 = MockCurrentUserV2(initialUser = user)
        var cardList = mutableListOf(StoredCardFactory.visa())

        val environment = environment().toBuilder()
            .apolloClientV2(object : MockApolloClientV2() {
                override fun getStoredCards(): Observable<List<StoredCard>> {
                    return Observable.just(cardList)
                }
            })
            .currentUserV2(currentUserV2)
            .build()

        setUpEnvironment(environment)

        val state = mutableListOf<LatePledgeCheckoutUIState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.latePledgeCheckoutUIState.toList(state)
        }

        // Before List changes
        assertEquals(
            state.last(),
            LatePledgeCheckoutUIState(
                storeCards = cardList,
                userEmail = "some@email.com"
            )
        )

        cardList = mutableListOf(StoredCardFactory.visa(), StoredCardFactory.discoverCard())

        viewModel.onNewCardSuccessfullyAdded()

        // After list is updated
        assertEquals(
            state.last(),
            LatePledgeCheckoutUIState(
                storeCards = cardList,
                userEmail = "some@email.com"
            )
        )
    }

    @Test
    fun `test_when_new_card_adding_fails_then_state_emits`() = runTest {
        val user = UserFactory.user()
        val currentUserV2 = MockCurrentUserV2(initialUser = user)
        val cardList = mutableListOf(StoredCardFactory.visa())

        val environment = environment().toBuilder()
            .apolloClientV2(object : MockApolloClientV2() {
                override fun getStoredCards(): Observable<List<StoredCard>> {
                    return Observable.just(cardList)
                }
            })
            .currentUserV2(currentUserV2)
            .build()

        setUpEnvironment(environment)

        val state = mutableListOf<LatePledgeCheckoutUIState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.latePledgeCheckoutUIState.toList(state)
        }

        viewModel.onNewCardFailed()

        assertEquals(
            state.last(),
            LatePledgeCheckoutUIState(
                storeCards = cardList,
                userEmail = "some@email.com"
            )
        )

        assertEquals(state.size, 2)
    }

    @Test
    fun `test when pledge_clicked_and_checkout_id_ and backingID not_provided then_error_action_is_called`() =
        runTest {
            val user = UserFactory.user()
            val currentUserV2 = MockCurrentUserV2(initialUser = user)
            val cardList = mutableListOf(StoredCardFactory.visa())

            val environment = environment().toBuilder()
                .apolloClientV2(object : MockApolloClientV2() {
                    override fun getStoredCards(): Observable<List<StoredCard>> {
                        return Observable.just(cardList)
                    }

                    override fun createCheckout(createCheckoutData: CreateCheckoutData): Observable<CheckoutPayment> {
                        return Observable.just(CheckoutPayment(100L, backing = Backing.builder().id(101L).build(), paymentUrl = ""))
                    }
                    override fun createPaymentIntent(createPaymentIntentInput: CreatePaymentIntentInput): Observable<String> {
                        return Observable.just("paymentIntent")
                    }

                    override fun validateCheckout(
                        checkoutId: String,
                        paymentIntentClientSecret: String,
                        paymentSourceId: String
                    ): Observable<PaymentValidationResponse> {
                        return Observable.just(
                            PaymentValidationResponse(
                                isValid = true,
                                messages = listOf()
                            )
                        )
                    }

                    override fun completeOnSessionCheckout(
                        checkoutId: String,
                        paymentIntentClientSecret: String,
                        paymentSourceId: String?,
                        paymentSourceReusable: Boolean
                    ): Observable<Pair<String, Boolean>> {
                        return Observable.just(Pair("Success", false))
                    }
                })
                .currentUserV2(currentUserV2)
                .build()

            val rw = RewardFactory.rewardWithShipping().toBuilder().latePledgeAmount(34.0).build()
            val project = ProjectFactory.project().toBuilder()
                .isInPostCampaignPledgingPhase(true)
                .postCampaignPledgingEnabled(true)
                .isBacking(false)
                .rewards(listOf(rw)).build()

            val addOns = listOf(rw, rw, rw)
            val rule = ShippingRuleFactory.germanyShippingRule().toBuilder().cost(3.0).build()
            val bonusAmount = 5.0

            val projectData = ProjectDataFactory.project(project = project)
            val pledgeData = PledgeData.with(PledgeFlowContext.LATE_PLEDGES, projectData, rw, addOns = addOns, bonusAmount = bonusAmount, shippingRule = rule)

            var errorActionCount = 0
            val state = mutableListOf<LatePledgeCheckoutUIState>()

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                setUpEnvironment(environment)

                viewModel.provideErrorAction {
                    errorActionCount++
                }

                viewModel.providePledgeData(pledgeData)

                viewModel.onPledgeButtonClicked(cardList.first())

                viewModel.latePledgeCheckoutUIState.toList(state)
            }
            advanceUntilIdle()

            assertEquals(state.last().storeCards, cardList)
            assertEquals(state.last().userEmail, "some@email.com")

            assertEquals(errorActionCount, 1)
        }

    @Test
    fun `test_when_pledge_clicked_and_checkout_id_provided_then_checkout_continues`() = runTest {
        val user = UserFactory.user()
        val currentUserV2 = MockCurrentUserV2(initialUser = user)
        val cardList = mutableListOf(StoredCardFactory.visa())

        var paymentIntentCalled = 0
        var validateCheckoutCalled = 0

        val environment = environment().toBuilder()
            .apolloClientV2(object : MockApolloClientV2() {
                override fun getStoredCards(): Observable<List<StoredCard>> {
                    return Observable.just(cardList)
                }

                override fun createCheckout(createCheckoutData: CreateCheckoutData): Observable<CheckoutPayment> {
                    return Observable.just(CheckoutPayment(100L, backing = Backing.builder().id(101L).build(), paymentUrl = ""))
                }
                override fun createPaymentIntent(createPaymentIntentInput: CreatePaymentIntentInput): Observable<String> {
                    paymentIntentCalled++
                    return Observable.just("paymentIntent")
                }

                override fun validateCheckout(
                    checkoutId: String,
                    paymentIntentClientSecret: String,
                    paymentSourceId: String
                ): Observable<PaymentValidationResponse> {
                    validateCheckoutCalled++
                    return Observable.just(
                        PaymentValidationResponse(
                            isValid = true,
                            messages = listOf()
                        )
                    )
                }

                override fun completeOnSessionCheckout(
                    checkoutId: String,
                    paymentIntentClientSecret: String,
                    paymentSourceId: String?,
                    paymentSourceReusable: Boolean
                ): Observable<Pair<String, Boolean>> {
                    return Observable.just(Pair("Success", false))
                }
            })
            .currentUserV2(currentUserV2)
            .build()

        val rw = RewardFactory.rewardWithShipping().toBuilder().latePledgeAmount(34.0).build()
        val project = ProjectFactory.project().toBuilder()
            .isInPostCampaignPledgingPhase(true)
            .postCampaignPledgingEnabled(true)
            .isBacking(false)
            .rewards(listOf(rw)).build()

        val addOns = listOf(rw, rw, rw)
        val rule = ShippingRuleFactory.germanyShippingRule().toBuilder().cost(3.0).build()
        val bonusAmount = 5.0

        val projectData = ProjectDataFactory.project(project = project)
        val pledgeData = PledgeData.with(PledgeFlowContext.LATE_PLEDGES, projectData, rw, addOns = addOns, bonusAmount = bonusAmount, shippingRule = rule)

        var errorActionCount = 0
        val state = mutableListOf<LatePledgeCheckoutUIState>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            setUpEnvironment(environment)

            viewModel.provideErrorAction {
                errorActionCount++
            }

            viewModel.providePledgeData(pledgeData)
            viewModel.provideCheckoutIdAndBacking(100L, Backing.builder().id(101L).build())

            viewModel.onPledgeButtonClicked(cardList.first())

            viewModel.latePledgeCheckoutUIState.toList(state)
        }
        advanceUntilIdle()

        assertEquals(state.last().storeCards, cardList)
        assertEquals(state.last().userEmail, "some@email.com")

        // Stripe will give an error since this is mock data
        assertEquals(errorActionCount, 1)
        assertEquals(validateCheckoutCalled, 1)
        assertEquals(paymentIntentCalled, 1)
    }

    @Test
    fun `test_when_complete3DSCheckout_called_with_no_values_then_errors`() = runTest {
        val user = UserFactory.user()
        val currentUserV2 = MockCurrentUserV2(initialUser = user)

        var completeOnSessionCheckoutCalled = 0

        val environment = environment().toBuilder()
            .apolloClientV2(object : MockApolloClientV2() {
                override fun completeOnSessionCheckout(
                    checkoutId: String,
                    paymentIntentClientSecret: String,
                    paymentSourceId: String?,
                    paymentSourceReusable: Boolean
                ): Observable<Pair<String, Boolean>> {
                    completeOnSessionCheckoutCalled++
                    return Observable.just(Pair("Success", false))
                }
            })
            .currentUserV2(currentUserV2)
            .build()

        setUpEnvironment(environment)

        var errorActionCount = 0

        viewModel.provideErrorAction {
            errorActionCount++
        }

        viewModel.completeOnSessionCheckoutFor3DS()

        assertEquals(errorActionCount, 1)
        assertEquals(completeOnSessionCheckoutCalled, 0)
    }

    @Test
    fun `test send PageViewed event`() = runTest {
        setUpEnvironment(environment())

        val rw = RewardFactory.rewardWithShipping().toBuilder().latePledgeAmount(34.0).build()
        val project = ProjectFactory.project().toBuilder().rewards(listOf(rw)).build()
        val addOns = listOf(rw, rw, rw)
        val rule = ShippingRuleFactory.germanyShippingRule().toBuilder().cost(3.0).build()
        val bonusAmount = 5.0

        val discover = StoredCardFactory.discoverCard()
        val visa = StoredCardFactory.visa()
        val cardsList = listOf(visa, discover)

        val currentUser = MockCurrentUserV2(UserFactory.user())
        setUpEnvironment(
            environment()
                .toBuilder()
                .currentUserV2(currentUser) // - mock the user
                .apolloClientV2(object : MockApolloClientV2() {
                    override fun getStoredCards(): Observable<List<StoredCard>> { // - mock the stored cards
                        return Observable.just(cardsList)
                    }

                    override fun userPrivacy(): Observable<UserPrivacy> { // - mock the user email and name
                        return Observable.just(
                            UserPrivacy("Hola holita", "hola@gmail.com", true, true, true, true, "MXN")
                        )
                    }

                    override fun createCheckout(createCheckoutData: CreateCheckoutData): Observable<CheckoutPayment> {
                        return Observable.just(CheckoutPayment(id = 3L, backing = BackingFactory.backing(rw), paymentUrl = "some url"))
                    }
                }).build()
        )

        val state = mutableListOf<LatePledgeCheckoutUIState>()
        val projectData = ProjectDataFactory.project(project = project)
        val pledgeData = PledgeData.with(PledgeFlowContext.LATE_PLEDGES, projectData, rw, addOns = addOns, bonusAmount = bonusAmount, shippingRule = rule)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {

            viewModel.providePledgeData(pledgeData)
            viewModel.userRewardSelection(rw)
            viewModel.sendPageViewedEvent()

            segmentTrack.assertValue(EventName.PAGE_VIEWED.eventName)
            viewModel.latePledgeCheckoutUIState.toList(state)
        }
    }

    @Test
    fun `test send CTAClicked event`() = runTest {
        setUpEnvironment(environment())

        val rw = RewardFactory.rewardWithShipping().toBuilder().latePledgeAmount(34.0).build()
        val project = ProjectFactory.project().toBuilder().rewards(listOf(rw)).build()
        val addOns = listOf(rw, rw, rw)
        val rule = ShippingRuleFactory.germanyShippingRule().toBuilder().cost(3.0).build()
        val bonusAmount = 5.0

        val discover = StoredCardFactory.discoverCard()
        val visa = StoredCardFactory.visa()
        val cardsList = listOf(visa, discover)

        val currentUser = MockCurrentUserV2(UserFactory.user())
        setUpEnvironment(
            environment()
                .toBuilder()
                .currentUserV2(currentUser) // - mock the user
                .apolloClientV2(object : MockApolloClientV2() {
                    override fun getStoredCards(): Observable<List<StoredCard>> { // - mock the stored cards
                        return Observable.just(cardsList)
                    }

                    override fun userPrivacy(): Observable<UserPrivacy> { // - mock the user email and name
                        return Observable.just(
                            UserPrivacy("Hola holita", "hola@gmail.com", true, true, true, true, "MXN")
                        )
                    }

                    override fun createCheckout(createCheckoutData: CreateCheckoutData): Observable<CheckoutPayment> {
                        return Observable.just(CheckoutPayment(id = 3L, backing = BackingFactory.backing(rw), paymentUrl = "some url"))
                    }
                }).build()
        )

        val state = mutableListOf<LatePledgeCheckoutUIState>()
        val projectData = ProjectDataFactory.project(project = project)
        val pledgeData = PledgeData.with(PledgeFlowContext.LATE_PLEDGES, projectData, rw, addOns = addOns, bonusAmount = bonusAmount, shippingRule = rule)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {

            viewModel.providePledgeData(pledgeData)
            viewModel.sendSubmitCTAEvent()

            segmentTrack.assertValue(EventName.CTA_CLICKED.eventName)
            viewModel.latePledgeCheckoutUIState.toList(state)
        }
    }

    @Test
    fun `Test VM init state when user and stored cards requests succeed will generate state with saved cards and user email`() = runTest {
        val discover = StoredCardFactory.discoverCard()
        val visa = StoredCardFactory.visa()
        val cardsList = listOf(visa, discover)

        val currentUser = MockCurrentUserV2(UserFactory.user())
        setUpEnvironment(
            environment()
                .toBuilder()
                .currentUserV2(currentUser) // - mock the user
                .apolloClientV2(object : MockApolloClientV2() {
                    override fun getStoredCards(): Observable<List<StoredCard>> { // - mock the stored cards
                        return Observable.just(cardsList)
                    }

                    override fun userPrivacy(): Observable<UserPrivacy> { // - mock the user email and name
                        return Observable.just(
                            UserPrivacy("Hola holita", "hola@gmail.com", true, true, true, true, "MXN")
                        )
                    }
                }).build()
        )

        val state = mutableListOf<LatePledgeCheckoutUIState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.latePledgeCheckoutUIState.toList(state)
        }

        assertEquals(state.size, 2)
        assertEquals(state.last().userEmail, "hola@gmail.com")
        assertEquals(state.last().storeCards, cardsList)
        assertEquals(state.last().storeCards.first(), cardsList.first())
        assertEquals(state.last().storeCards.last(), cardsList.last())
        assertEquals(state.last().isLoading, false)
    }

    @Test
    fun `Test VM error init state when user or stored cards requests fail will generate state without saved cards or user email`() = runTest {

        val currentUser = MockCurrentUserV2(UserFactory.user())
        setUpEnvironment(
            environment()
                .toBuilder()
                .currentUserV2(currentUser) // - mock the user
                .apolloClientV2(object : MockApolloClientV2() {
                    override fun getStoredCards(): Observable<List<StoredCard>> { // - mock the stored cards
                        return Observable.error(Throwable("Something went wrong"))
                    }

                    override fun userPrivacy(): Observable<UserPrivacy> { // - mock the user email and name
                        return Observable.error(Throwable("Something went wrong"))
                    }
                }).build()
        )

        val state = mutableListOf<LatePledgeCheckoutUIState>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.latePledgeCheckoutUIState.toList(state)
        }

        assertEquals(state.size, 1)
        assertEquals(state.last().userEmail, "")
        assertEquals(state.last().storeCards, emptyList<StoredCard>())
    }
}
