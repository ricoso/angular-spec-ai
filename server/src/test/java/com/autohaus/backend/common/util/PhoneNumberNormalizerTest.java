package com.autohaus.backend.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumberNormalizerTest {

    @Test
    void toSoftnetFormat_leadingZero_replacedWithGermanCountryCode() {
        assertEquals("+4917012345678", PhoneNumberNormalizer.toSoftnetFormat("017012345678"));
    }

    @Test
    void toSoftnetFormat_alreadyInternational_returnedUnchanged() {
        assertEquals("+491701234567", PhoneNumberNormalizer.toSoftnetFormat("+491701234567"));
    }

    @Test
    void toSoftnetFormat_internalWhitespacePreserved() {
        assertEquals("+49170 123 45678", PhoneNumberNormalizer.toSoftnetFormat("0170 123 45678"));
    }

    @Test
    void toSoftnetFormat_null_returnedUnchanged() {
        assertNull(PhoneNumberNormalizer.toSoftnetFormat(null));
    }

    @Test
    void toSoftnetFormat_blank_returnedUnchanged() {
        assertEquals("", PhoneNumberNormalizer.toSoftnetFormat(""));
    }

    @Test
    void toSoftnetFormat_noRecognizedPrefix_returnedUnchanged() {
        assertEquals("17012345678", PhoneNumberNormalizer.toSoftnetFormat("17012345678"));
    }

    @Test
    void toSoftnetFormat_surroundingWhitespace_trimmedBeforeNormalization() {
        assertEquals("+4917012345678", PhoneNumberNormalizer.toSoftnetFormat("  017012345678  "));
    }
}
