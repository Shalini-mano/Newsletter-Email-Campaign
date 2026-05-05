
package com.newsletter.service;
import com.newsletter.model.*;
import com.newsletter.dto.request.CampaignRequest;
import com.newsletter.dto.response.ApiResponse;
import com.newsletter.exception.BadRequestException;
import com.newsletter.exception.ResourceNotFoundException;
import com.newsletter.repository.CampaignRepository;
import com.newsletter.repository.MailingListRepository;
import com.newsletter.repository.UserRepository;
import com.newsletter.service.impl.CampaignServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private MailingListRepository mailingListRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private CampaignServiceImpl campaignService;

    private User user;
    private MailingList mailingList;

    @BeforeEach
    void init() {
        user = new User("john", "john@example.com", "hashed");
        user.setId("user-001");

        mailingList = new MailingList("Test List", "Sample list", "user-001");
        mailingList.setId("list-001");

        mailingList.getSubscribers().add(
                new Subscriber(UUID.randomUUID().toString(), "Alice", "alice@mail.com")
        );
        mailingList.getSubscribers().add(
                new Subscriber(UUID.randomUUID().toString(), "Bob", "bob@mail.com")
        );
    }

    // ───────────────── CREATE ─────────────────

    @Test
    void shouldCreateDraftCampaign_whenValidRequestProvided() {
        // given
        CampaignRequest.Create request = new CampaignRequest.Create();
        request.setName("Launch Campaign");
        request.setSubject("We are live!");
        request.setContent("Exciting updates");
        request.setMailingListId("list-001");

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(mailingListRepository.findByIdAndOwnerId("list-001", user.getId()))
                .thenReturn(Optional.of(mailingList));

        Campaign saved = new Campaign("Launch Campaign", "We are live!", "Exciting updates", user.getId());
        saved.setId("camp-001");

        when(campaignRepository.save(any())).thenReturn(saved);

        // when
        ApiResponse.CampaignResponse response =
                campaignService.createCampaign(request, "john");

        // then
        assertThat(response.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(response.getMailingListId()).isEqualTo("list-001");
    }

    @Test
    void shouldCreateDraftWithoutMailingList() {
        // given
        CampaignRequest.Create request = new CampaignRequest.Create();
        request.setName("Simple Draft");
        request.setSubject("Hello");
        request.setContent("Body");

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        Campaign saved = new Campaign("Simple Draft", "Hello", "Body", user.getId());
        saved.setId("camp-002");

        when(campaignRepository.save(any())).thenReturn(saved);

        // when
        ApiResponse.CampaignResponse response =
                campaignService.createCampaign(request, "john");

        // then
        assertThat(response.getMailingListId()).isNull();
        assertThat(response.getStatus()).isEqualTo(CampaignStatus.DRAFT);
    }

    // ───────────────── UPDATE ─────────────────

    @Test
    void shouldUpdateCampaignDetails_whenDraftCampaignExists() {
        // given
        Campaign campaign = new Campaign("Old", "OldSub", "OldBody", user.getId());
        campaign.setId("camp-001");
        campaign.setStatus(CampaignStatus.DRAFT);

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(campaignRepository.findByIdAndOwnerId("camp-001", user.getId()))
                .thenReturn(Optional.of(campaign));

        when(campaignRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CampaignRequest.Update request = new CampaignRequest.Update();
        request.setName("Updated Name");

        // when
        ApiResponse.CampaignResponse response =
                campaignService.updateCampaign("camp-001", request, "john");

        // then
        assertThat(response.getName()).isEqualTo("Updated Name");
    }

    @Test
    void shouldThrowException_whenUpdatingSentCampaign() {
        // given
        Campaign campaign = new Campaign("Sent", "Sub", "Body", user.getId());
        campaign.setId("camp-001");
        campaign.setStatus(CampaignStatus.SENT);

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(campaignRepository.findByIdAndOwnerId("camp-001", user.getId()))
                .thenReturn(Optional.of(campaign));

        // then
        assertThatThrownBy(() ->
                campaignService.updateCampaign("camp-001", new CampaignRequest.Update(), "john"))
                .isInstanceOf(BadRequestException.class);
    }

    // ───────────────── SCHEDULE ─────────────────

    @Test
    void shouldScheduleCampaign_whenFutureDateProvided() {
        // given
        Campaign campaign = new Campaign("Schedule", "Sub", "Body", user.getId());
        campaign.setId("camp-001");
        campaign.setStatus(CampaignStatus.DRAFT);

        LocalDateTime future = LocalDateTime.now().plusDays(2);

        CampaignRequest.Schedule request = new CampaignRequest.Schedule();
        request.setScheduledAt(future);
        request.setMailingListId("list-001");

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(campaignRepository.findByIdAndOwnerId("camp-001", user.getId()))
                .thenReturn(Optional.of(campaign));
        when(mailingListRepository.findByIdAndOwnerId("list-001", user.getId()))
                .thenReturn(Optional.of(mailingList));
        when(campaignRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        ApiResponse.CampaignResponse response =
                campaignService.scheduleCampaign("camp-001", request, "john");

        // then
        assertThat(response.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);
    }

    @Test
    void shouldFailScheduling_whenPastDateGiven() {
        Campaign campaign = new Campaign("Test", "Sub", "Body", user.getId());
        campaign.setId("camp-001");
        campaign.setStatus(CampaignStatus.DRAFT);

        CampaignRequest.Schedule request = new CampaignRequest.Schedule();
        request.setScheduledAt(LocalDateTime.now().minusHours(2));

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(campaignRepository.findByIdAndOwnerId("camp-001", user.getId()))
                .thenReturn(Optional.of(campaign));

        assertThatThrownBy(() ->
                campaignService.scheduleCampaign("camp-001", request, "john"))
                .isInstanceOf(BadRequestException.class);
    }

    // ───────────────── GET ─────────────────

    @Test
    void shouldReturnCampaigns_whenNoFilterApplied() {
        Campaign c1 = new Campaign("A", "S", "C", user.getId());
        Campaign c2 = new Campaign("B", "S", "C", user.getId());

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(campaignRepository.findByOwnerId(eq(user.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(c1, c2)));

        Page<ApiResponse.CampaignResponse> result =
                campaignService.getAllCampaigns("john", null, 0, 10);

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void shouldThrowNotFound_whenCampaignMissing() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(campaignRepository.findByIdAndOwnerId("invalid", user.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                campaignService.getCampaignById("invalid", "john"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ───────────────── DELETE ─────────────────

    @Test
    void shouldDeleteCampaign_whenValidDraftCampaign() {
        Campaign campaign = new Campaign("Delete", "Sub", "Body", user.getId());
        campaign.setId("camp-001");
        campaign.setStatus(CampaignStatus.DRAFT);

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(campaignRepository.findByIdAndOwnerId("camp-001", user.getId()))
                .thenReturn(Optional.of(campaign));

        campaignService.deleteCampaign("camp-001", "john");

        verify(campaignRepository).delete(campaign);
    }

    @Test
    void shouldThrowError_whenDeletingSentCampaign() {
        Campaign campaign = new Campaign("Sent", "Sub", "Body", user.getId());
        campaign.setId("camp-001");
        campaign.setStatus(CampaignStatus.SENT);

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(campaignRepository.findByIdAndOwnerId("camp-001", user.getId()))
                .thenReturn(Optional.of(campaign));

        assertThatThrownBy(() ->
                campaignService.deleteCampaign("camp-001", "john"))
                .isInstanceOf(BadRequestException.class);
    }

    // ───────────────── SCHEDULER ─────────────────

    @Test
    void shouldMarkCampaignAsSent_whenDueCampaignExists() {
        Campaign campaign = new Campaign("Due", "Sub", "Body", user.getId());
        campaign.setId("camp-001");
        campaign.setStatus(CampaignStatus.SCHEDULED);
        campaign.setScheduledAt(LocalDateTime.now().minusMinutes(5));
        campaign.setMailingListId("list-001");

        when(campaignRepository.findDueCampaigns(eq(CampaignStatus.SCHEDULED), any()))
                .thenReturn(List.of(campaign));
        when(mailingListRepository.findById("list-001"))
                .thenReturn(Optional.of(mailingList));
        when(campaignRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        campaignService.processDueCampaigns();

        verify(campaignRepository).save(argThat(c ->
                c.getStatus() == CampaignStatus.SENT && c.getSentAt() != null
        ));
    }

    @Test
    void shouldDoNothing_whenNoCampaignsDue() {
        when(campaignRepository.findDueCampaigns(any(), any()))
                .thenReturn(List.of());

        campaignService.processDueCampaigns();

        verify(campaignRepository, never()).save(any());
    }
}
