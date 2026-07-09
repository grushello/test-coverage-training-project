package lv.bootcamp.shelter.service;

import lv.bootcamp.shelter.audit.AuditLogger;
import lv.bootcamp.shelter.audit.RejectionReason;
import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.model.*;
import lv.bootcamp.shelter.repository.AdopterRepository;
import lv.bootcamp.shelter.repository.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Write tests for AdoptionEligibilityService.
 * The class and mocks are set up — the rest is yours.
 */


@ExtendWith(MockitoExtension.class)
//Not enough time to reduce unnecessary stubbings, line below prevents exceptions from that
@MockitoSettings(strictness = Strictness.LENIENT)
class AdoptionEligibilityServiceTest {

    @Mock
    private AdopterRepository adopterRepository;

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private AdoptionEligibilityService service;

    @Test
    void shouldRejectWhenAdopterNotFound() {

        when(adopterRepository.findById(1L)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(1L, 1L);

        assertFalse(result.approved());
        assertEquals(RejectionReasons.ADOPTER_NOT_FOUND, result.reason());

        verifyNoInteractions(animalRepository, notificationClient, auditLogger);
    }
    @Test
    void shouldRejectWhenAnimalNotFound() {

        Adopter adopter = mock(Adopter.class);

        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter));

        when(animalRepository.findById(1L)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(1L, 1L);

        assertFalse(result.approved());
        assertEquals(RejectionReasons.ANIMAL_NOT_FOUND, result.reason());

        verifyNoInteractions(notificationClient, auditLogger);
    }
    @Test
    void shouldRejectWhenAnimalIsNotAvailable() {

        Adopter adopter = validAdopter();
        Animal animal = validAnimal();

        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter));

        when(animalRepository.findById(1L)).thenReturn(Optional.of(animal));

        when(animal.getStatus()).thenReturn(AnimalStatus.ADOPTED);


        AdoptionResult result = service.evaluateAdoption(1L,1L);

        assertFalse(result.approved());
        assertEquals(RejectionReasons.ANIMAL_NOT_AVAILABLE, result.reason());

        verifyNoInteractions(notificationClient, auditLogger);
    }
    @Test
    void shouldApproveValid() {

        Adopter adopter = validAdopter();
        Animal animal = validAnimal();

        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter));

        when(animalRepository.findById(1L)).thenReturn(Optional.of(animal));


        AdoptionResult result = service.evaluateAdoption(1L,1L);

        assertTrue(result.approved());
        verify(auditLogger).logApproval(eq(1L), eq(1L), anyInt());
    }
    @Test
    void shouldApproveValid18YearOld() {

        Adopter adopter = validAdopter();
        Animal animal = validAnimal();

        when(adopter.getAge()).thenReturn(18);


        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter));

        when(animalRepository.findById(1L)).thenReturn(Optional.of(animal));


        AdoptionResult result = service.evaluateAdoption(1L,1L);

        assertTrue(result.approved());

        verify(auditLogger).logApproval(eq(1L), eq(1L), anyInt());
    }
    @Test
    void shouldRejectUnderageAdopter() {

        Adopter adopter = validAdopter();

        when(adopter.getAge()).thenReturn(17);

        Animal animal = validAnimal();

        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter));

        when(animalRepository.findById(1L)).thenReturn(Optional.of(animal));


        AdoptionResult result = service.evaluateAdoption(1L,1L);

        assertFalse(result.approved());

        verify(auditLogger).logRejection(1L, 1L, RejectionReason.UNDERAGE);
        verifyNoInteractions(notificationClient);
    }

    @Test
    void shouldRejectWhenPetLimitReached() {

        Adopter adopter = validAdopter();
        Animal animal = validAnimal();

        when(adopter.getCurrentPetCount()).thenReturn(3);

        when(adopter.isLargeProperty()).thenReturn(false);

        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter));

        when(animalRepository.findById(1L)).thenReturn(Optional.of(animal));

        AdoptionResult result = service.evaluateAdoption(1L, 1L);


        assertFalse(result.approved());

        assertEquals(RejectionReasons.PET_LIMIT_REACHED, result.reason());

        verify(auditLogger).logRejection(1L, 1L, RejectionReason.PET_LIMIT_REACHED);

        verifyNoInteractions(notificationClient);
    }

    @Test
    void shouldAllowLargePropertyOwnerWithFivePetLimit() {

        Adopter adopter = validAdopter();
        Animal animal = validAnimal();

        when(adopter.isLargeProperty()).thenReturn(true);

        when(adopter.getCurrentPetCount()).thenReturn(4);

        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter));

        when(animalRepository.findById(1L)).thenReturn(Optional.of(animal));


        AdoptionResult result = service.evaluateAdoption(1L, 1L);

        assertTrue(result.approved());

        verify(notificationClient).sendApprovalNotification("test@test.com", "Rex");
    }

    @Test
    void shouldRejectExoticAnimalWithoutPermit() {

        Adopter adopter = validAdopter();
        Animal animal = validAnimal();


        when(animal.getType()).thenReturn(AnimalType.BIRD);

        when(adopter.isExoticPermit()).thenReturn(false);


        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter));

        when(animalRepository.findById(1L)).thenReturn(Optional.of(animal));


        AdoptionResult result = service.evaluateAdoption(1L, 1L);


        assertFalse(result.approved());

        assertEquals(RejectionReasons.EXOTIC_PERMIT_REQUIRED, result.reason());

        verify(auditLogger).logRejection(1L, 1L, RejectionReason.NO_EXOTIC_PERMIT);

        verifyNoInteractions(notificationClient);
    }

    @Test
    void shouldApproveExoticAnimalWithPermit() {

        Adopter adopter = validAdopter();
        Animal animal = validAnimal();


        when(animal.getType()).thenReturn(AnimalType.BIRD);

        when(adopter.isExoticPermit()).thenReturn(true);


        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter));

        when(animalRepository.findById(1L)).thenReturn(Optional.of(animal));


        AdoptionResult result = service.evaluateAdoption(1L, 1L);


        assertTrue(result.approved());

        verify(notificationClient).sendApprovalNotification("test@test.com", "Rex");
    }

    @Test
    void shouldCalculatePriorityScoreCorrectly() {

        Adopter adopter = mock(Adopter.class);
        Animal animal = mock(Animal.class);


        when(adopter.getPreviousAdoptions()).thenReturn(4);

        when(adopter.isLargeProperty()).thenReturn(true);

        when(adopter.getCurrentPetCount()).thenReturn(2);


        when(animal.getAge()).thenReturn(10);


        int score = service.calculatePriorityScore(adopter, animal);


        assertEquals(46, score);
    }

    @Test
    void shouldReturnNonNegativePriorityScore() {

        Adopter adopter = mock(Adopter.class);
        Animal animal = mock(Animal.class);


        when(adopter.getPreviousAdoptions()).thenReturn(0);

        when(adopter.isLargeProperty()).thenReturn(false);

        when(adopter.getCurrentPetCount()).thenReturn(100);

        int score = service.calculatePriorityScore(adopter, animal);

        assertEquals(0, score);
    }

    @Test
    void shouldGivePreviousAdoptionBonus() {

        Adopter adopter = mock(Adopter.class);
        Animal animal = mock(Animal.class);

        when(adopter.getPreviousAdoptions()).thenReturn(1);

        when(adopter.isLargeProperty()).thenReturn(false);

        when(adopter.getCurrentPetCount()).thenReturn(0);

        int score = service.calculatePriorityScore(adopter, animal);

        assertEquals(10, score);
    }
    @Test
    void shouldGiveBonusForMorThanThirdAdoption() {

        Adopter adopter = mock(Adopter.class);
        Animal animal = mock(Animal.class);


        when(adopter.getPreviousAdoptions()).thenReturn(4);

        when(adopter.isLargeProperty()).thenReturn(false);

        when(adopter.getCurrentPetCount()).thenReturn(0);

        int score = service.calculatePriorityScore(adopter, animal);

        assertEquals(15, score);
    }
    @Test
    void shouldRejectLargePropertyOwnerWhenFivePetsAlreadyOwned() {

        Adopter adopter = validAdopter();
        Animal animal = validAnimal();


        when(adopter.isLargeProperty()).thenReturn(true);

        when(adopter.getCurrentPetCount()).thenReturn(5);


        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter));

        when(animalRepository.findById(1L)).thenReturn(Optional.of(animal));


        AdoptionResult result = service.evaluateAdoption(1L, 1L);


        assertFalse(result.approved());

        assertEquals(RejectionReasons.PET_LIMIT_REACHED, result.reason());


        verify(auditLogger).logRejection(1L, 1L, RejectionReason.PET_LIMIT_REACHED);
    }
    private Adopter validAdopter() {

        Adopter adopter = mock(Adopter.class);

        when(adopter.getAge()).thenReturn(30);

        when(adopter.getCurrentPetCount()).thenReturn(0);

        when(adopter.isLargeProperty()).thenReturn(false);

        when(adopter.isExoticPermit()).thenReturn(false);

        when(adopter.getEmail()).thenReturn("test@test.com");

        return adopter;
    }


    private Animal validAnimal() {

        Animal animal = mock(Animal.class);

        when(animal.getStatus()).thenReturn(AnimalStatus.AVAILABLE);

        when(animal.getType()).thenReturn(AnimalType.DOG);

        when(animal.getName()).thenReturn("Rex");

        return animal;
    }

}
