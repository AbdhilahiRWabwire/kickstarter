package com.kickstarter.viewmodels

import com.kickstarter.KSRobolectricTestCase
import com.kickstarter.libs.Environment
import com.kickstarter.libs.MockCurrentUserV2
import com.kickstarter.libs.utils.EventName
import com.kickstarter.mock.MockCurrentConfigV2
import com.kickstarter.mock.factories.BackingFactory
import com.kickstarter.mock.factories.ConfigFactory
import com.kickstarter.mock.factories.ProjectDataFactory
import com.kickstarter.mock.factories.ProjectFactory
import com.kickstarter.mock.factories.RewardFactory
import com.kickstarter.mock.factories.ShippingRuleFactory
import com.kickstarter.mock.factories.ShippingRulesEnvelopeFactory
import com.kickstarter.mock.factories.UserFactory
import com.kickstarter.mock.services.MockApolloClientV2
import com.kickstarter.models.Backing
import com.kickstarter.models.Project
import com.kickstarter.models.Reward
import com.kickstarter.services.apiresponses.ShippingRulesEnvelope
import com.kickstarter.ui.data.ProjectData
import com.kickstarter.viewmodels.projectpage.FlowUIState
import com.kickstarter.viewmodels.projectpage.RewardSelectionUIState
import com.kickstarter.viewmodels.projectpage.RewardsSelectionViewModel
import com.kickstarter.viewmodels.usecases.GetShippingRulesUseCase
import com.kickstarter.viewmodels.usecases.ShippingRulesState
import io.reactivex.Observable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RewardsSelectionViewModelTest : KSRobolectricTestCase() {

    private lateinit var viewModel: RewardsSelectionViewModel

    private fun createViewModel(environment: Environment = environment(), useCase: GetShippingRulesUseCase? = null) {
        viewModel =
            RewardsSelectionViewModel.Factory(environment, useCase).create(RewardsSelectionViewModel::class.java)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun test_providing_project_should_initialize_UIState() = runTest {
        createViewModel()

        val testRewards = (0..5).map { Reward.builder().title("$it").id(it.toLong()).isAvailable(true).build() }
        val testBacking =
            Backing.builder().reward(testRewards[2]).rewardId(testRewards[2].id()).build()
        val testProject = Project.builder().rewards(testRewards).backing(testBacking).build()
        val testProjectData = ProjectData.builder().project(testProject).build()

        viewModel.provideProjectData(testProjectData)

        val state = mutableListOf<RewardSelectionUIState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.rewardSelectionUIState.toList(state)
        }

        // 1 from initialization, 1 from providing project data
        assert(state.size == 2)
        assertEquals(
            state.last(),
            RewardSelectionUIState(
                initialRewardIndex = 2,
                project = testProjectData,
            )
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun test_selecting_reward_with_addOns_no_previous_backing() = runTest {
        createViewModel()

        val testRewards =
            (0..5).map { Reward.builder().title("$it").id(it.toLong()).isAvailable(true).hasAddons(true).build() }
        val testProject = Project.builder().rewards(testRewards).build()
        val testProjectData = ProjectData.builder().project(testProject).build()

        viewModel.provideProjectData(testProjectData)

        val uiState = mutableListOf<RewardSelectionUIState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.rewardSelectionUIState.toList(uiState)
        }

        val flowState = mutableListOf<FlowUIState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.flowUIRequest.toList(flowState)
        }

        assert(uiState.size == 2)
        assert(flowState.size == 0)
        assertEquals(
            uiState.last(),
            RewardSelectionUIState(
                initialRewardIndex = 0,
                project = testProjectData,
            )
        )

        viewModel.onUserRewardSelection(testRewards[2])

        assert(uiState.size == 3)
        assert(flowState.size == 1)
        assertEquals(
            flowState.last(),
            FlowUIState(currentPage = 1, expanded = true)
        )

        this@RewardsSelectionViewModelTest.segmentTrack.assertValue(EventName.CTA_CLICKED.eventName)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun test_selecting_reward_no_addOns_no_previous_backing() = runTest {
        createViewModel()

        val testRewards =
            (0..5).map { Reward.builder().title("$it").id(it.toLong()).isAvailable(true).hasAddons(false).build() }
        val testProject = Project.builder().rewards(testRewards).build()
        val testProjectData = ProjectData.builder().project(testProject).build()

        viewModel.provideProjectData(testProjectData)

        val uiState = mutableListOf<RewardSelectionUIState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.rewardSelectionUIState.toList(uiState)
        }

        val flowState = mutableListOf<FlowUIState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.flowUIRequest.toList(flowState)
        }

        assert(uiState.size == 2)
        assert(flowState.size == 0)
        assertEquals(
            uiState.last(),
            RewardSelectionUIState(
                initialRewardIndex = 0,
                project = testProjectData,
            )
        )

        viewModel.onUserRewardSelection(testRewards[2])

        assert(uiState.size == 3)
        assert(flowState.size == 1)
        assertEquals(
            flowState.last(),
            FlowUIState(currentPage = 1, expanded = true)
        )

        this@RewardsSelectionViewModelTest.segmentTrack.assertValue(EventName.CTA_CLICKED.eventName)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun test_selecting_reward_with_addOns_previous_backing_same_selection() = runTest {
        createViewModel()

        val testRewards =
            (0..5).map { Reward.builder().title("$it").id(it.toLong()).isAvailable(true).hasAddons(true).build() }
        val testBacking =
            Backing.builder().reward(testRewards[2]).rewardId(testRewards[2].id()).build()
        val testProject =
            Project.builder().rewards(testRewards).backing(testBacking).isBacking(true).build()
        val testProjectData =
            ProjectData.builder().project(testProject).backing(testBacking).build()

        viewModel.provideProjectData(testProjectData)

        val uiState = mutableListOf<RewardSelectionUIState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.rewardSelectionUIState.toList(uiState)
        }

        val flowState = mutableListOf<FlowUIState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.flowUIRequest.toList(flowState)
        }

        assert(uiState.size == 2)
        assert(flowState.size == 0)
        assertEquals(
            uiState.last(),
            RewardSelectionUIState(
                initialRewardIndex = 2,
                project = testProjectData,
            )
        )

        viewModel.onUserRewardSelection(testRewards[2])

        assert(uiState.size == 3)
        assert(flowState.size == 1)
        assertEquals(
            flowState.last(),
            FlowUIState(currentPage = 1, expanded = true)
        )

        this@RewardsSelectionViewModelTest.segmentTrack.assertValue(EventName.CTA_CLICKED.eventName)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun test_selecting_reward_with_addOns_previous_backing_different_selection() = runTest {
        createViewModel()

        val testRewards =
            (0..5).map { Reward.builder().title("$it").id(it.toLong()).isAvailable(true).hasAddons(true).build() }
        val testBacking =
            Backing.builder().reward(testRewards[3]).rewardId(testRewards[3].id()).build()
        val testProject =
            Project.builder().rewards(testRewards).backing(testBacking).isBacking(true).build()
        val testProjectData =
            ProjectData.builder().project(testProject).backing(testBacking).build()

        viewModel.provideProjectData(testProjectData)

        val uiState = mutableListOf<RewardSelectionUIState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.rewardSelectionUIState.toList(uiState)
        }

        val flowState = mutableListOf<FlowUIState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.flowUIRequest.toList(flowState)
        }

        assert(uiState.size == 2)
        assert(flowState.size == 0)
        assertEquals(
            uiState.last(),
            RewardSelectionUIState(
                initialRewardIndex = 3,
                project = testProjectData,
            )
        )

        viewModel.onUserRewardSelection(testRewards[2])

        assert(uiState.size == 3)
        assert(flowState.size == 1)
        assertEquals(
            flowState.last(),
            FlowUIState(currentPage = 1, expanded = true)
        )

        this@RewardsSelectionViewModelTest.segmentTrack.assertValue(EventName.CTA_CLICKED.eventName)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun test_selecting_reward_with_no_addOns_previous_backing_no_addOns() = runTest {
        createViewModel()

        val testRewards =
            (0..5).map { Reward.builder().title("$it").id(it.toLong()).isAvailable(true).hasAddons(false).build() }
        val testBacking =
            Backing.builder().reward(testRewards[3]).rewardId(testRewards[3].id()).build()
        val testProject =
            Project.builder().rewards(testRewards).backing(testBacking).isBacking(true).build()
        val testProjectData =
            ProjectData.builder().project(testProject).backing(testBacking).build()

        viewModel.provideProjectData(testProjectData)

        val uiState = mutableListOf<RewardSelectionUIState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.rewardSelectionUIState.toList(uiState)
        }

        val flowState = mutableListOf<FlowUIState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.flowUIRequest.toList(flowState)
        }

        assert(uiState.size == 2)
        assert(flowState.size == 0)
        assertEquals(
            uiState.last(),
            RewardSelectionUIState(
                initialRewardIndex = 3,
                project = testProjectData,
            )
        )

        viewModel.onUserRewardSelection(testRewards[2])

        assert(uiState.size == 3)
        assert(flowState.size == 1)
        assertEquals(
            flowState.last(),
            FlowUIState(currentPage = 1, expanded = true)
        )

        this@RewardsSelectionViewModelTest.segmentTrack.assertValue(EventName.CTA_CLICKED.eventName)
    }

    @Test
    fun `Test rewards list filtered when given a Germany location and a Project with unavailable rewards and mixed types of shipping`() = runTest {
        val testShippingRulesList = ShippingRulesEnvelopeFactory.shippingRules()
        val testRewards: List<Reward> = (0..8).map {
            if (it == 5)
                Reward.builder().title("$it").id(it.toLong()).isAvailable(true).hasAddons(it != 2)
                    .pledgeAmount(3.0)
                    .shippingPreference(Reward.ShippingPreference.RESTRICTED.name)
                    .shippingPreferenceType(Reward.ShippingPreference.RESTRICTED)
                    .shippingRules((listOf(ShippingRuleFactory.mexicoShippingRule())))
                    .build()
            else if (it == 3)
                Reward.builder().title("$it").id(it.toLong()).isAvailable(true).hasAddons(it != 2)
                    .pledgeAmount(3.0)
                    .shippingPreference(Reward.ShippingPreference.RESTRICTED.name)
                    .shippingPreferenceType(Reward.ShippingPreference.RESTRICTED)
                    .shippingRules((listOf(ShippingRuleFactory.germanyShippingRule())))
                    .build()
            else if (it == 0)
                RewardFactory.noReward()
            else if (it % 2 == 0)
                Reward.builder().title("$it").id(it.toLong()).isAvailable(true).hasAddons(it != 2)
                    .pledgeAmount(3.0)
                    .shippingPreference(Reward.ShippingPreference.UNRESTRICTED.name)
                    .shippingPreferenceType(Reward.ShippingPreference.UNRESTRICTED)
                    .build()
            else
                Reward.builder().title("$it").id(it.toLong()).isAvailable(false).hasAddons(it != 2)
                    .pledgeAmount(3.0)
                    .shippingPreference(Reward.ShippingPreference.RESTRICTED.name)
                    .shippingPreferenceType(Reward.ShippingPreference.RESTRICTED)
                    .shippingRules((listOf(ShippingRuleFactory.mexicoShippingRule())))
                    .build()
        }

        val testProject = Project.builder().rewards(testRewards).build()
        val testProjectData = ProjectData.builder().project(testProject).build()

        val config = ConfigFactory.configForCA()
        val currentConfig = MockCurrentConfigV2()
        currentConfig.config(config)

        val user = UserFactory.canadianUser()
        val env = environment()
            .toBuilder()
            .currentConfig2(currentConfig)
            .currentUserV2(MockCurrentUserV2(user))
            .build()

        val apolloClient = object : MockApolloClientV2() {
            override fun getShippingRules(reward: Reward): Observable<ShippingRulesEnvelope> {
                return Observable.just(testShippingRulesList)
            }
        }

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val shippingUiState = mutableListOf<ShippingRulesState>()

        backgroundScope.launch(dispatcher) {
            val useCase = GetShippingRulesUseCase(apolloClient, testProject, config, this, dispatcher)
            createViewModel(env, useCase)
            viewModel.provideProjectData(testProjectData)

            viewModel.shippingUIState.toList(shippingUiState)
        }
        advanceUntilIdle() // wait until all state emissions completed
        viewModel.selectedShippingRule(ShippingRuleFactory.germanyShippingRule())
        advanceTimeBy(600) // account for de delay within GetShippingRulesUseCase.filterBySelectedRule

        // - Available rewards should be those available AND able to ship to germany
        val filteredRewards = mutableListOf(
            testRewards[0],
            testRewards[2],
            testRewards[3],
            testRewards[4],
            testRewards[6],
            testRewards[8]
        )

        assertEquals(shippingUiState.size, 5)
        assertEquals(shippingUiState[3].loading, true)
        assertEquals(shippingUiState[4].loading, false)

        // - make sure the uiState output reward list is filtered
        assertEquals(shippingUiState.last().filteredRw.size, filteredRewards.size)

        val obtained = shippingUiState.last().filteredRw
        assertEquals(
            obtained,
            filteredRewards
        )

        assertEquals(obtained.first(), RewardFactory.noReward())
        assertEquals(obtained[2].shippingRules()?.first(), ShippingRuleFactory.germanyShippingRule())
    }

    @Test
    fun `test send analytic event trackRewardsCarouselViewed() when the currentPage is rewards and is expanded mode`() {
        createViewModel()

        val projectData = ProjectDataFactory.project(ProjectFactory.project())

        viewModel.sendEvent(expanded = true, currentPage = 0, projectData)
        this@RewardsSelectionViewModelTest.segmentTrack.assertValue(EventName.PAGE_VIEWED.eventName)
    }

    @Test
    fun `test analytic event trackRewardsCarouselViewed() not sent when the currentPage is not rewards or not expanded`() {
        createViewModel()

        val projectData = ProjectDataFactory.project(ProjectFactory.project())

        viewModel.sendEvent(expanded = true, currentPage = 1, projectData)
        this@RewardsSelectionViewModelTest.segmentTrack.assertNoValues()

        viewModel.sendEvent(expanded = false, currentPage = 0, projectData)
        this@RewardsSelectionViewModelTest.segmentTrack.assertNoValues()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Default Location when Backing Project is backed location, and list of shipping rules for "restricted" is all places available for all restricted rewards without duplicated`() = runTest {
        val testShippingRulesList = ShippingRulesEnvelopeFactory.shippingRules().shippingRules()

        val rw1 = RewardFactory
            .reward()
            .toBuilder()
            .id(1)
            .shippingPreference(Reward.ShippingPreference.RESTRICTED.name)
            .shippingPreferenceType(Reward.ShippingPreference.RESTRICTED)
            .shippingRules((listOf(testShippingRulesList.first())))
            .build()

        val rw2 = RewardFactory
            .reward()
            .toBuilder()
            .id(2)
            .shippingPreference(Reward.ShippingPreference.RESTRICTED.name)
            .shippingPreferenceType(Reward.ShippingPreference.RESTRICTED)
            .shippingRules((listOf(testShippingRulesList.first())))
            .build()

        val rw3 = RewardFactory
            .reward()
            .toBuilder()
            .id(3)
            .shippingPreference(Reward.ShippingPreference.RESTRICTED.name)
            .shippingPreferenceType(Reward.ShippingPreference.RESTRICTED)
            .shippingRules(listOf(testShippingRulesList[2]))
            .build()

        val user = UserFactory.user()
        val backing = BackingFactory.backing(rw1).toBuilder()
            .location(testShippingRulesList.first().location())
            .locationId(testShippingRulesList.first().location()?.id())
            .locationName(testShippingRulesList.first().location()?.displayableName())
            .build()

        val project = ProjectFactory.project().toBuilder()
            .rewards(listOf(rw1, rw2, rw3))
            .backing(backing)
            .isBacking(true)
            .build()

        val projectData = ProjectDataFactory.project(project, null, null)

        val config = ConfigFactory.configForCA()
        val currentConfig = MockCurrentConfigV2()
        currentConfig.config(config)

        val env = environment()
            .toBuilder()
            .currentConfig2(currentConfig)
            .currentUserV2(MockCurrentUserV2(user))
            .build()

        val apolloClient = requireNotNull(env.apolloClientV2())

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val shippingUiState = mutableListOf<ShippingRulesState>()
        backgroundScope.launch(dispatcher) {
            val useCase = GetShippingRulesUseCase(apolloClient, project, config, this, dispatcher)
            createViewModel(env, useCase)
            viewModel.provideProjectData(projectData)
            viewModel.shippingUIState.toList(shippingUiState)
        }

        advanceUntilIdle() // wait until all state emissions completed

        assertEquals(shippingUiState.size, 2)
        assertEquals(shippingUiState.last().selectedShippingRule.location()?.id(), testShippingRulesList.first().location()?.id())
        assertNotSame(shippingUiState.last().shippingRules, testShippingRulesList)
        assertEquals(shippingUiState.last().shippingRules.size, 2) // the 3 available shipping rules
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `config is from Canada and available rules are global so Default Shipping is Canada, and list of shipping Rules provided matches all available reward global shipping`() = runTest {
        val rw = RewardFactory
            .reward()
            .toBuilder()
            .shippingPreference(Reward.ShippingPreference.UNRESTRICTED.name)
            .shippingPreferenceType(Reward.ShippingPreference.UNRESTRICTED)
            .build()
        val user = UserFactory.user()
        val project = ProjectFactory.project().toBuilder().rewards(listOf(rw, rw, rw)).build()
        val projectData = ProjectDataFactory.project(project, null, null)

        val testShippingRulesList = ShippingRulesEnvelopeFactory.shippingRules()
        val apolloClient = object : MockApolloClientV2() {
            override fun getShippingRules(reward: Reward): Observable<ShippingRulesEnvelope> {
                return Observable.just(testShippingRulesList)
            }
        }

        val config = ConfigFactory.configForCA()
        val currentConfig = MockCurrentConfigV2()
        currentConfig.config(config)

        val env = environment()
            .toBuilder()
            .currentConfig2(currentConfig)
            .apolloClientV2(apolloClient)
            .currentUserV2(MockCurrentUserV2(user))
            .build()

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val shippingUiState = mutableListOf<ShippingRulesState>()
        backgroundScope.launch(dispatcher) {
            val useCase = GetShippingRulesUseCase(apolloClient, project, config, this, dispatcher)
            createViewModel(env, useCase)
            viewModel.provideProjectData(projectData)
            viewModel.shippingUIState.toList(shippingUiState)
        }

        advanceUntilIdle() // wait until all state emissions completed

        assertEquals(shippingUiState.size, 3)
        assertEquals(shippingUiState.last().selectedShippingRule.location()?.name(), "Canada")
        assertEquals(shippingUiState.last().shippingRules, testShippingRulesList.shippingRules())
    }
}