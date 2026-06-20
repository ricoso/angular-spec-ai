/**
 * Type-safe Translations (TypeScript-only, NO JSON!)
 * All UI texts are multilingual (DE + EN + UK + FR)
 *
 * Format: Nested objects for better type safety
 * Template: {{ header.cart.button | translate }} (via i18nKeys + TranslatePipe)
 * TypeScript: translateService.instant(i18nKeys.header.cart.button)
 */
export const translations = {
  de: {
    app: {
      title: 'Autohaus GmbH',
      subtitle: 'Fahrzeugauswahl',
      skipLink: 'Zum Hauptinhalt springen',
      error: {
        title: 'Es ist ein Fehler aufgetreten',
        subtext: 'Wir können momentan keine Daten laden. Bitte versuchen Sie es erneut oder kommen Sie später wieder.',
        offlineHint: 'Bitte prüfen Sie Ihre Internetverbindung.',
        retryButton: 'Erneut versuchen',
        retryButtonAriaLabel: 'Erneut versuchen, die Daten zu laden',
        detailsToggle: 'Technische Details anzeigen',
        codeLabel: 'Fehler-Code',
        statusLabel: 'Status',
        timestampLabel: 'Zeitstempel',
        requestIdLabel: 'Request-ID'
      }
    },

    header: {
      accessibility: {
        button: 'Barrierefreiheit',
        buttonLabel: 'Barrierefreiheit Einstellungen',
        fontSize: {
          label: 'Schriftgröße',
          small: 'Klein',
          normal: 'Normal',
          large: 'Groß',
          xLarge: 'Sehr groß'
        },
        highContrast: 'Hoher Kontrast',
        reducedMotion: 'Reduzierte Bewegung',
        language: {
          label: 'Sprache'
        }
      },
      logo: {
        alt: 'Firmenlogo'
      },
      close: {
        buttonLabel: 'Buchung schließen und zurück zur Webseite'
      },
      cart: {
        button: 'Warenkorb',
        title: 'Warenkorb',
        empty: 'Ihr Warenkorb ist leer',
        placeholder: 'Inhalt wird bald verfügbar',
        summary: 'Sie haben {count} Service(s) ausgewählt',
        brandLabel: 'Marke',
        locationLabel: 'Standort',
        servicesLabel: 'Services',
        appointmentLabel: 'Termin',
        badge: {
          ariaLabel: 'Artikel im Warenkorb'
        }
      }
    },

    booking: {
      wizard: {
        location: 'Standort',
        brand: 'Marke',
        service: 'Service',
        notes: 'Hinweise',
        appointment: 'Termin',
        vehicle: 'Fahrzeug',
        overview: 'Übersicht'
      },
      branch: {
        infoLabel: 'Ihr Standort',
        infoSeparator: '—'
      },
      brand: {
        title: 'Welche Fahrzeugmarke fahren Sie?',
        subtitle: 'Bitte wählen Sie die gewünschte Marke aus.',
        backButton: 'Zurück',
        noBrandsAvailable: 'Für diesen Standort sind derzeit keine Marken verfügbar.',
        ariaGroupLabel: 'Markenauswahl',
        ariaNavLabel: 'Seitennavigation'
      },
      location: {
        title: 'Buchen Sie Ihren Werkstatt-Termin online!',
        subtitle: 'Wählen Sie Ihren Standort — die Markenlogos helfen Ihnen bei der Orientierung',
        ariaGroupLabel: 'Standortauswahl',
        ariaNavLabel: 'Seitennavigation',
        continueButton: 'Weiter'
      },
      services: {
        title: 'Welche Services möchten Sie buchen?',
        subtitle: 'Wählen Sie die gewünschten Services aus.',
        ariaGroupLabel: 'Verfügbare Services',
        continueButton: 'Weiter',
        backButton: 'Zurück',
        confirmButton: 'Bestätigen',
        deselectButton: 'Abwählen',
        inspection: {
          title: 'Inspektion',
          description: 'Services rund um Ihr Fahrzeug: von Inspektion über Ölwechsel bis Fahrzeugvermessung.',
          options: {
            dialogAcceptance: 'Dialogannahme',
            inspection: 'Inspektion',
            oilChange: 'Ölwechsel-Service',
            brakeCheck: 'Bremsen prüfen (kostenlos) und bei Bedarf erneuern',
            wiperBlades: 'Wischerblätter erneuern',
            maintenanceContract: 'Interesse an einem Wartungsvertrag',
            emergencyOil: '1 Liter Notfallöl'
          }
        },
        tuv: {
          title: 'TÜV',
          description: 'Hier klicken und Termin zur Haupt- & Abgasuntersuchung oder UVV-Prüfung buchen.',
          options: {
            tuv: 'TÜV',
            uvvInspection: 'UVV-Prüfung (für Dienstwagen & gewerblich genutzte Fahrzeuge)',
            acCleaning: 'Klimaanlagenreinigung (149,00 € inkl. MwSt.)'
          }
        },
        brakeFluid: {
          title: 'Wechsel Bremsflüssigkeit',
          description: 'Wechsel der Bremsflüssigkeit Ihres Fahrzeugs ist fällig? Einfach hier buchen.'
        },
        tireChange: {
          title: 'Räderwechsel',
          description: 'Alle Services rund um Ihre Räder und Reifen finden Sie hier.',
          options: {
            bringOwnTires: 'Räderwechsel - ich bringe meine Räder mit',
            storedTires: 'Räderwechsel - meine Räder sind eingelagert',
            pickupDelivery: 'Hol & Bringservice',
            storeTires: 'Meine angelieferten Räder sollen bitte eingelagert werden'
          }
        },
        actionsChecks: {
          title: 'Aktionen / Checks',
          description: 'Sie wollen Ihr Fahrzeug durchchecken lassen oder fit für die aktuelle Jahreszeit machen? Hier den passenden Service buchen.',
          options: {
            warrantyCheck: 'Kostenloser Check vor Ablauf der Herstellergarantie',
            safetyCheck: 'Sicherheits-Check / Saisonaler Check',
            fluidCheck: 'Flüssigkeitsstände prüfen (kostenlos) und bei Bedarf auffüllen',
            batteryCheck: 'Batterie Check (Kostenlos im Verbund)'
          }
        },
        repair: {
          title: 'Reparatur / Beanstandung',
          description: 'Sie haben anderweitige Anliegen bezüglich Ihres Fahrzeugs? Hier klicken und im folgenden Kommentarfeld konkretisieren.',
          options: {
            diagnostics: 'Allgemeine Diagnosearbeiten',
            noises: 'Geräusche',
            acMalfunction: 'Klimaanlage ohne Funktion'
          }
        },
        bodywork: {
          title: 'Karosserie / Frontscheibe wechseln',
          description: 'Sie benötigen eine neue Windschutzscheibe oder einen Kostenvoranschlag? Hier einfach und schnell buchen.',
          options: {
            costEstimate: 'Kostenvoranschlag / Gutachten nach Unfall',
            windshieldReplacement: 'Windschutzscheibe erneuern'
          }
        }
      },
      appointment: {
        title: 'Wählen Sie den für Sie passenden Tag und Uhrzeit aus',
        calendarLink: 'Hier sehen Sie weitere freie Termine in unserem Werkstattkalender',
        backButton: 'Zurück',
        continueButton: 'Weiter',
        ariaGroupLabel: 'Terminvorschläge',
        navAriaLabel: 'Seitennavigation'
      },
      workshopCalendar: {
        title: 'Hier sehen Sie weitere freie Termine in unserem Werkstattkalender',
        description: 'Wählen Sie Ihren Wunschtermin. Wir zeigen Ihnen alle freien Termine ab diesem Tag an.',
        desiredDateLabel: 'Ihr Wunschtermin:',
        datePlaceholder: 'Wunschtermin wählen',
        hintBefore: 'Wählen Sie im Kalender einen gewünschten Termin aus und wir zeigen Ihnen die nächsten verfügbaren Termine',
        hintAfter: 'Wir haben folgende ab dem von Ihnen ausgewählten Datum verfügbaren Termine für Sie gefunden. Klicken Sie auf eine Uhrzeit, um den Termin auszuwählen.',
        dateInputAriaLabel: 'Wunschtermin eingeben im Format TT.MM.JJJJ',
        slotsAriaLabel: 'Verfügbare Uhrzeiten',
        backButton: 'Zurück',
        continueButton: 'Weiter',
        navAriaLabel: 'Seitennavigation'
      },
      notes: {
        pageTitle: 'Bitte geben Sie uns weitere Hinweise zu Ihrer Buchung',
        sectionTitle: 'Möchten Sie uns noch etwas zu Ihrer Buchung mitteilen?',
        textareaPlaceholder: 'Bitte tragen Sie hier Ihre Nachricht an uns ein.',
        textareaHint: 'Bitte tragen Sie hier Ihre Nachricht an uns ein.',
        charCountAriaLabel: '{current} von {max} Zeichen verwendet',
        hintsTitle: 'Wichtige Hinweise zu Ihren ausgewählten Services',
        backButton: 'Zurück',
        continueButton: 'Weiter',
        mobilityOptions: {
          title: 'Mobilitätsoptionen (kostenpflichtig)',
          label: 'Wählen',
          none: 'Keine Auswahl',
          compactCar: 'Kleinwagen',
          midRange: 'Mittelklasse',
          luxury: 'Oberklasse'
        },
        rentCategory: {
          title: 'Mietwagen-Kategorie',
          label: 'Kategorie wählen',
          none: 'Kein Mietwagen'
        },
        rentCategoryPrice: {
          label: 'Mietwagen-Kategorie',
          unitDay: 'pro Tag',
          unitHour: 'pro Stunde',
          srLabel: 'Mietwagen-Kategorie ausgewählt'
        },
        appointmentPreference: {
          title: 'Terminpräferenz',
          label: 'Wählen',
          anytime: 'Jederzeit',
          morning: 'Vormittags',
          afternoon: 'Nachmittags'
        },
        callback: {
          title: 'Weitere Optionen: Rückruf',
          label: 'Rückruf',
          no: 'Nein',
          yes: 'Ja'
        },
        notification: {
          title: 'Benachrichtigung',
          label: 'Wie möchten Sie benachrichtigt werden?',
          email: 'E-Mail',
          sms: 'SMS'
        }
      },
      carinformation: {
        title: 'Bitte geben Sie uns letzte Informationen rund um Ihren Termin',
        returningCustomer: {
          title: 'Schon einmal bei uns gewesen?',
          description: 'Dann rufen Sie Ihre Daten automatisch mit Eingabe Ihrer E-Mail-Adresse ab.',
          button: 'Jetzt Daten abrufen!'
        },
        form: {
          customerTitle: 'Ihre Daten',
          vehicleTitle: 'Fahrzeugdaten',
          email: { label: 'E-Mail Adresse', placeholder: 'max@mustermann.de',
            error: { required: 'Bitte geben Sie Ihre E-Mail-Adresse ein.', invalid: 'Bitte geben Sie eine gültige E-Mail-Adresse ein.' } },
          salutation: { label: 'Anrede', placeholder: 'Bitte wählen', mr: 'Herr', ms: 'Frau',
            error: { required: 'Bitte wählen Sie eine Anrede.' } },
          firstName: { label: 'Vorname', placeholder: 'Max',
            error: { required: 'Bitte geben Sie Ihren Vornamen ein.', lettersOnly: 'Vorname darf nur Buchstaben enthalten.' } },
          lastName: { label: 'Nachname', placeholder: 'Mustermann',
            error: { required: 'Bitte geben Sie Ihren Nachnamen ein.', lettersOnly: 'Nachname darf nur Buchstaben enthalten.' } },
          street: { label: 'Straße und Haus Nr.', placeholder: 'Musterweg 1',
            error: { required: 'Bitte geben Sie Ihre Straße und Hausnummer ein.' } },
          postalCode: { label: 'Postleitzahl', placeholder: '30159',
            error: { required: 'Bitte geben Sie Ihre Postleitzahl ein.', digitsOnly: 'Postleitzahl darf nur Zahlen enthalten.' } },
          city: { label: 'Wohnort', placeholder: 'Berlin',
            error: { required: 'Bitte geben Sie Ihren Wohnort ein.', lettersOnly: 'Wohnort darf nur Buchstaben enthalten.' } },
          mobilePhone: { label: 'Mobilfunknummer', placeholder: '017012345678',
            hint: 'Format: 017012345678 oder +491701234567',
            error: { required: 'Bitte geben Sie Ihre Mobilfunknummer ein.', digitsOnly: 'Mobilfunknummer darf nur Zahlen enthalten.', startsWithZero: 'Mobilfunknummer muss mit 0 oder +49 beginnen.' } },
          licensePlate: { label: 'Kfz. Kennzeichen', placeholder: 'XX-XX1234',
            error: { required: 'Bitte geben Sie Ihr Kfz-Kennzeichen ein.', invalidFormat: 'Bitte geben Sie ein gültiges Kennzeichen ein (z.B. B-MS1234).' } },
          mileage: { label: 'Kilometerstand', placeholder: '5000',
            error: { required: 'Bitte geben Sie den Kilometerstand ein.', digitsOnly: 'Kilometerstand darf nur Zahlen enthalten.' } },
vin: { label: 'FIN', placeholder: 'WDB8XXXXXXA123456', infoLink: 'Erklärung der FIN',
            error: { required: 'Bitte geben Sie die Fahrzeugidentifikationsnummer ein.', invalidLength: 'Die FIN muss genau 17 Zeichen enthalten.', invalidFormat: 'Die FIN darf nur Buchstaben und Zahlen enthalten.' } },
          privacy: { consent: 'Ich willige in die Verarbeitung meiner personenbezogenen Daten zum Zwecke der Online-Terminvereinbarung ein. Informationen',
            privacyLink: 'hier',
            error: { required: 'Bitte bestätigen Sie die Datenschutzerklärung.' } },
          requiredHint: 'Pflichtfelder sind mit * gekennzeichnet'
        },
        navigation: { back: 'Zurück', continue: 'Zur Buchungsübersicht' }
      },
      bookingOverview: {
        title: 'Übersicht',
        subtitle: 'Bitte prüfen Sie Ihre Angaben bevor Sie den Termin versenden.',
        ariaGridLabel: 'Buchungsübersicht',
        ariaNavLabel: 'Seitennavigation',
        edit: 'Ändern',
        tiles: {
          appointment: {
            title: 'Wunschtermin',
            dateLabel: 'Datum',
            timeLabel: 'Uhrzeit'
          },
          services: {
            title: 'Gewählter Service',
            intro: 'Folgende Leistungen werden in',
            introSuffix: 'für Sie angefragt:',
            empty: 'Keine Leistungen ausgewählt.'
          },
          personalData: {
            title: 'Ihre Daten',
            nameLabel: 'Name',
            streetLabel: 'Straße',
            cityLabel: 'Ort',
            phoneLabel: 'Telefon',
            emailLabel: 'E-Mail',
            brandLabel: 'Marke',
            licensePlateLabel: 'Kennzeichen',
            mileageLabel: 'Kilometerstand',
            mileageUnit: 'km'
          },
          extras: {
            title: 'Hinweise & Optionen',
            mobilityLabel: 'Mobilität',
            appointmentPreferenceLabel: 'Terminpräferenz',
            callbackLabel: 'Rückruf',
            noteLabel: 'Nachricht'
          }
        },
        navigation: {
          back: 'Zurück',
          submit: 'Jetzt anfragen',
          submitting: 'Wird gesendet...',
          submitted: 'Anfrage gesendet'
        },
        confirmation: {
          message: 'Vielen Dank, Ihr Termin ist reserviert. Wir freuen uns auf Ihren Besuch.'
        }
      },
      api: {
        loading: 'Daten werden geladen...',
        submitting: 'Ihre Anfrage wird gesendet...',
        errorTitle: 'Fehler beim Laden',
        errorServerError: 'Der Server ist momentan nicht erreichbar. Bitte versuchen Sie es später erneut.',
        errorTimeout: 'Die Anfrage hat zu lange gedauert. Bitte versuchen Sie es erneut.',
        errorTokenFailed: 'Die Sitzung konnte nicht hergestellt werden. Bitte laden Sie die Seite neu.',
        errorUnknown: 'Ein unbekannter Fehler ist aufgetreten. Bitte versuchen Sie es erneut.',
        errorSubmit: 'Die Buchung konnte nicht gesendet werden. Bitte versuchen Sie es erneut.',
        retryButton: 'Erneut versuchen',
        fallbackAddress: 'Musterstraße 1, 00000 Musterhausen'
      },
      overlay: {
        cartLabel: 'Warenkorb',
        accessibilityLabel: 'Barrierefreiheit',
        cartBadgeLabel: '{count} Artikel im Warenkorb',
        selectionTitle: 'Ihre Auswahl',
        locationLabel: 'Standort',
        brandLabel: 'Marke',
        servicesLabel: 'Leistungen',
        appointmentLabel: 'Termin',
        emptySelection: 'Noch keine Auswahl getroffen.'
      }
    }
  },

  en: {
    app: {
      title: 'Autohaus GmbH',
      subtitle: 'Fahrzeugauswahl',
      skipLink: 'Skip to main content',
      error: {
        title: 'An error occurred',
        subtext: 'We can\'t load data right now. Please try again or come back later.',
        offlineHint: 'Please check your internet connection.',
        retryButton: 'Retry',
        retryButtonAriaLabel: 'Retry loading the data',
        detailsToggle: 'Show technical details',
        codeLabel: 'Error code',
        statusLabel: 'Status',
        timestampLabel: 'Timestamp',
        requestIdLabel: 'Request ID'
      }
    },

    header: {
      accessibility: {
        button: 'Accessibility',
        buttonLabel: 'Accessibility Settings',
        fontSize: {
          label: 'Font Size',
          small: 'Small',
          normal: 'Normal',
          large: 'Large',
          xLarge: 'Extra Large'
        },
        highContrast: 'High Contrast',
        reducedMotion: 'Reduced Motion',
        language: {
          label: 'Language'
        }
      },
      logo: {
        alt: 'Company Logo'
      },
      close: {
        buttonLabel: 'Close booking and return to website'
      },
      cart: {
        button: 'Shopping Cart',
        title: 'Shopping Cart',
        empty: 'Your cart is empty',
        placeholder: 'Content coming soon',
        summary: 'You have selected {count} service(s)',
        brandLabel: 'Brand',
        locationLabel: 'Location',
        servicesLabel: 'Services',
        appointmentLabel: 'Appointment',
        badge: {
          ariaLabel: 'items in cart'
        }
      }
    },

    booking: {
      wizard: {
        location: 'Location',
        brand: 'Brand',
        service: 'Service',
        notes: 'Notes',
        appointment: 'Appointment',
        vehicle: 'Vehicle',
        overview: 'Overview'
      },
      branch: {
        infoLabel: 'Your location',
        infoSeparator: '—'
      },
      brand: {
        title: 'Which vehicle brand do you drive?',
        subtitle: 'Please choose the brand you need.',
        backButton: 'Back',
        noBrandsAvailable: 'No brands are currently available for this location.',
        ariaGroupLabel: 'Brand selection',
        ariaNavLabel: 'Page navigation'
      },
      location: {
        title: 'Book your workshop appointment online!',
        subtitle: 'Select your location — brand logos help you navigate',
        ariaGroupLabel: 'Location selection',
        ariaNavLabel: 'Page navigation',
        continueButton: 'Continue'
      },
      services: {
        title: 'Which services would you like to book?',
        subtitle: 'Select the desired services.',
        ariaGroupLabel: 'Available services',
        continueButton: 'Continue',
        backButton: 'Back',
        confirmButton: 'Confirm',
        deselectButton: 'Deselect',
        inspection: {
          title: 'Inspection',
          description: 'Services for your vehicle: from inspection to oil change and vehicle alignment.',
          options: {
            dialogAcceptance: 'Dialog acceptance',
            inspection: 'Inspection',
            oilChange: 'Oil change service',
            brakeCheck: 'Check brakes (free) and replace if needed',
            wiperBlades: 'Replace wiper blades',
            maintenanceContract: 'Interest in a maintenance contract',
            emergencyOil: '1 litre emergency oil'
          }
        },
        tuv: {
          title: 'MOT',
          description: 'Click here to book an appointment for MOT inspection or UVV check.',
          options: {
            tuv: 'MOT',
            uvvInspection: 'UVV inspection (for company cars & commercially used vehicles)',
            acCleaning: 'Air conditioning cleaning (€149.00 incl. VAT)'
          }
        },
        brakeFluid: {
          title: 'Brake Fluid Change',
          description: 'Is your vehicle\'s brake fluid change due? Simply book it here.'
        },
        tireChange: {
          title: 'Tire Change',
          description: 'All services for your wheels and tires can be found here.',
          options: {
            bringOwnTires: 'Tire change - I bring my own tires',
            storedTires: 'Tire change - my tires are in storage',
            pickupDelivery: 'Pick-up & delivery service',
            storeTires: 'Please store my delivered tires'
          }
        },
        actionsChecks: {
          title: 'Actions / Checks',
          description: 'Want to have your vehicle checked or ready for the current season? Book the right service here.',
          options: {
            warrantyCheck: 'Free check before manufacturer warranty expires',
            safetyCheck: 'Safety check / seasonal check',
            fluidCheck: 'Check fluid levels (free) and top up if needed',
            batteryCheck: 'Battery check (free in network)'
          }
        },
        repair: {
          title: 'Repair / Complaint',
          description: 'Do you have other concerns regarding your vehicle? Click here and specify in the comment field below.',
          options: {
            diagnostics: 'General diagnostics',
            noises: 'Noises',
            acMalfunction: 'Air conditioning malfunction'
          }
        },
        bodywork: {
          title: 'Bodywork / Windshield Replacement',
          description: 'Need a new windshield or a cost estimate? Book quickly and easily here.',
          options: {
            costEstimate: 'Cost estimate / assessment after accident',
            windshieldReplacement: 'Replace windshield'
          }
        }
      },
      appointment: {
        title: 'Select your preferred day and time',
        calendarLink: 'Here you can see more available appointments in our workshop calendar',
        backButton: 'Back',
        continueButton: 'Continue',
        ariaGroupLabel: 'Appointment suggestions',
        navAriaLabel: 'Page navigation'
      },
      workshopCalendar: {
        title: 'Here you can see further available appointments in our workshop calendar',
        description: 'Select your desired date. We will show you all available appointments from that day onwards.',
        desiredDateLabel: 'Your desired date:',
        datePlaceholder: 'Select desired date',
        hintBefore: 'Select a desired date in the calendar and we will show you the next available appointments',
        hintAfter: 'We found the following available appointments from your selected date. Click on a time to select the appointment.',
        dateInputAriaLabel: 'Enter desired date in format DD.MM.YYYY',
        slotsAriaLabel: 'Available times',
        backButton: 'Back',
        continueButton: 'Continue',
        navAriaLabel: 'Page navigation'
      },
      notes: {
        pageTitle: 'Please provide further notes for your booking',
        sectionTitle: 'Would you like to tell us anything else about your booking?',
        textareaPlaceholder: 'Please enter your message to us here.',
        textareaHint: 'Please enter your message to us here.',
        charCountAriaLabel: '{current} of {max} characters used',
        hintsTitle: 'Important notes about your selected services',
        backButton: 'Back',
        continueButton: 'Continue',
        mobilityOptions: {
          title: 'Mobility options (chargeable)',
          label: 'Select',
          none: 'No selection',
          compactCar: 'Compact car',
          midRange: 'Mid-range',
          luxury: 'Luxury'
        },
        rentCategory: {
          title: 'Rental car category',
          label: 'Select category',
          none: 'No rental car'
        },
        rentCategoryPrice: {
          label: 'Rental car category',
          unitDay: 'per day',
          unitHour: 'per hour',
          srLabel: 'Rental car category selected'
        },
        appointmentPreference: {
          title: 'Appointment preference',
          label: 'Select',
          anytime: 'Anytime',
          morning: 'Morning',
          afternoon: 'Afternoon'
        },
        callback: {
          title: 'Further options: Callback',
          label: 'Callback',
          no: 'No',
          yes: 'Yes'
        },
        notification: {
          title: 'Notification',
          label: 'How would you like to be notified?',
          email: 'Email',
          sms: 'SMS'
        }
      },
      carinformation: {
        title: 'Please provide us with final information about your appointment',
        returningCustomer: {
          title: 'Been with us before?',
          description: 'Retrieve your data automatically by entering your email address.',
          button: 'Retrieve my data!'
        },
        form: {
          customerTitle: 'Your Details',
          vehicleTitle: 'Vehicle Data',
          email: { label: 'Email Address', placeholder: 'max@example.com',
            error: { required: 'Please enter your email address.', invalid: 'Please enter a valid email address.' } },
          salutation: { label: 'Salutation', placeholder: 'Please select', mr: 'Mr.', ms: 'Ms.',
            error: { required: 'Please select a salutation.' } },
          firstName: { label: 'First Name', placeholder: 'John',
            error: { required: 'Please enter your first name.', lettersOnly: 'First name may only contain letters.' } },
          lastName: { label: 'Last Name', placeholder: 'Doe',
            error: { required: 'Please enter your last name.', lettersOnly: 'Last name may only contain letters.' } },
          street: { label: 'Street and House Number', placeholder: '1 Example Street',
            error: { required: 'Please enter your street and house number.' } },
          postalCode: { label: 'Postal Code', placeholder: '30159',
            error: { required: 'Please enter your postal code.', digitsOnly: 'Postal code may only contain digits.' } },
          city: { label: 'City', placeholder: 'Berlin',
            error: { required: 'Please enter your city.', lettersOnly: 'City may only contain letters.' } },
          mobilePhone: { label: 'Mobile Phone Number', placeholder: '017012345678',
            hint: 'Format: 017012345678 or +491701234567',
            error: { required: 'Please enter your mobile phone number.', digitsOnly: 'Mobile number may only contain digits.', startsWithZero: 'Mobile number must start with 0 or +49.' } },
          licensePlate: { label: 'License Plate', placeholder: 'XX-XX1234',
            error: { required: 'Please enter your license plate.', invalidFormat: 'Please enter a valid license plate (e.g. B-MS1234).' } },
          mileage: { label: 'Mileage', placeholder: '5000',
            error: { required: 'Please enter the current mileage.', digitsOnly: 'Mileage may only contain digits.' } },
vin: { label: 'VIN', placeholder: 'WDB8XXXXXXA123456', infoLink: 'Explanation of VIN',
            error: { required: 'Please enter the vehicle identification number.', invalidLength: 'The VIN must be exactly 17 characters.', invalidFormat: 'The VIN may only contain letters and digits.' } },
          privacy: { consent: 'I consent to the processing of my personal data for the purpose of online appointment booking. More information',
            privacyLink: 'here',
            error: { required: 'Please accept the privacy policy.' } },
          requiredHint: 'Required fields are marked with *'
        },
        navigation: { back: 'Back', continue: 'To Booking Overview' }
      },
      bookingOverview: {
        title: 'Overview',
        subtitle: 'Please review your details before submitting the appointment.',
        ariaGridLabel: 'Booking summary',
        ariaNavLabel: 'Page navigation',
        edit: 'Edit',
        tiles: {
          appointment: {
            title: 'Desired Appointment',
            dateLabel: 'Date',
            timeLabel: 'Time'
          },
          services: {
            title: 'Selected Service',
            intro: 'The following services will be requested at',
            introSuffix: 'for you:',
            empty: 'No services selected.'
          },
          personalData: {
            title: 'Your Details',
            nameLabel: 'Name',
            streetLabel: 'Street',
            cityLabel: 'City',
            phoneLabel: 'Phone',
            emailLabel: 'Email',
            brandLabel: 'Brand',
            licensePlateLabel: 'License Plate',
            mileageLabel: 'Mileage',
            mileageUnit: 'km'
          },
          extras: {
            title: 'Notes & Options',
            mobilityLabel: 'Mobility',
            appointmentPreferenceLabel: 'Appointment Preference',
            callbackLabel: 'Callback',
            noteLabel: 'Message'
          }
        },
        navigation: {
          back: 'Back',
          submit: 'Request Now',
          submitting: 'Sending...',
          submitted: 'Request Sent'
        },
        confirmation: {
          message: 'Thank you, your appointment is reserved. We look forward to your visit.'
        }
      },
      api: {
        loading: 'Loading data...',
        submitting: 'Sending your request...',
        errorTitle: 'Loading Error',
        errorServerError: 'The server is currently unavailable. Please try again later.',
        errorTimeout: 'The request took too long. Please try again.',
        errorTokenFailed: 'The session could not be established. Please reload the page.',
        errorUnknown: 'An unknown error occurred. Please try again.',
        errorSubmit: 'The booking could not be sent. Please try again.',
        retryButton: 'Retry',
        fallbackAddress: 'Musterstraße 1, 00000 Musterhausen'
      },
      overlay: {
        cartLabel: 'Shopping cart',
        accessibilityLabel: 'Accessibility',
        cartBadgeLabel: '{count} items in cart',
        selectionTitle: 'Your selection',
        locationLabel: 'Location',
        brandLabel: 'Brand',
        servicesLabel: 'Services',
        appointmentLabel: 'Appointment',
        emptySelection: 'No selections made yet.'
      }
    }
  },

  uk: {
    app: {
      title: 'Autohaus GmbH',
      subtitle: 'Fahrzeugauswahl',
      skipLink: 'Перейти до основного вмісту',
      error: {
        title: 'Сталася помилка',
        subtext: 'Наразі не вдається завантажити дані. Будь ласка, спробуйте знову або поверніться пізніше.',
        offlineHint: 'Будь ласка, перевірте підключення до Інтернету.',
        retryButton: 'Повторити',
        retryButtonAriaLabel: 'Повторити завантаження даних',
        detailsToggle: 'Показати технічні деталі',
        codeLabel: 'Код помилки',
        statusLabel: 'Статус',
        timestampLabel: 'Часова мітка',
        requestIdLabel: 'ID запиту'
      }
    },

    header: {
      accessibility: {
        button: 'Доступність',
        buttonLabel: 'Налаштування доступності',
        fontSize: {
          label: 'Розмір шрифту',
          small: 'Малий',
          normal: 'Звичайний',
          large: 'Великий',
          xLarge: 'Дуже великий'
        },
        highContrast: 'Високий контраст',
        reducedMotion: 'Зменшена анімація',
        language: {
          label: 'Мова'
        }
      },
      logo: {
        alt: 'Логотип компанії'
      },
      close: {
        buttonLabel: 'Закрити бронювання та повернутися на сайт'
      },
      cart: {
        button: 'Кошик',
        title: 'Кошик',
        empty: 'Ваш кошик порожній',
        placeholder: 'Вміст незабаром буде доступний',
        summary: 'Ви обрали {count} послуг(у)',
        brandLabel: 'Марка',
        locationLabel: 'Місцезнаходження',
        servicesLabel: 'Послуги',
        appointmentLabel: 'Запис',
        badge: {
          ariaLabel: 'товарів у кошику'
        }
      }
    },

    booking: {
      wizard: {
        location: 'Місце',
        brand: 'Марка',
        service: 'Сервіс',
        notes: 'Примітки',
        appointment: 'Зустріч',
        vehicle: 'Автомобіль',
        overview: 'Огляд'
      },
      branch: {
        infoLabel: 'Ваше місце',
        infoSeparator: '—'
      },
      brand: {
        title: 'Яку марку ви хочете обслуговувати?',
        subtitle: 'У вашому місці доступні наступні марки',
        backButton: 'Назад',
        noBrandsAvailable: 'Для цього місця наразі немає доступних марок.',
        ariaGroupLabel: 'Вибір марки',
        ariaNavLabel: 'Навігація сторінки'
      },
      location: {
        title: 'Забронюйте візит до автосервісу онлайн!',
        subtitle: 'Оберіть ваше місце — логотипи марок допоможуть зорієнтуватися',
        ariaGroupLabel: 'Вибір місця',
        ariaNavLabel: 'Навігація сторінки',
        continueButton: 'Далі'
      },
      services: {
        title: 'Які послуги ви хотіли б замовити?',
        subtitle: 'Оберіть потрібні послуги.',
        ariaGroupLabel: 'Доступні послуги',
        continueButton: 'Далі',
        backButton: 'Назад',
        confirmButton: 'Підтвердити',
        deselectButton: 'Скасувати вибір',
        inspection: {
          title: 'Інспекція',
          description: 'Послуги для вашого автомобіля: від інспекції до заміни масла та вирівнювання.',
          options: {
            dialogAcceptance: 'Діалогове прийняття',
            inspection: 'Інспекція',
            oilChange: 'Заміна масла',
            brakeCheck: 'Перевірка гальм (безкоштовно) та заміна за потреби',
            wiperBlades: 'Заміна щіток склоочисника',
            maintenanceContract: 'Зацікавленість у договорі обслуговування',
            emergencyOil: '1 літр аварійної оливи'
          }
        },
        tuv: {
          title: 'Техогляд',
          description: 'Натисніть тут, щоб записатися на техогляд або перевірку UVV.',
          options: {
            tuv: 'Техогляд',
            uvvInspection: 'Перевірка UVV (для службових та комерційних автомобілів)',
            acCleaning: 'Очищення кондиціонера (149,00 € з ПДВ)'
          }
        },
        brakeFluid: {
          title: 'Заміна гальмівної рідини',
          description: 'Час замінити гальмівну рідину? Просто запишіться тут.'
        },
        tireChange: {
          title: 'Заміна коліс',
          description: 'Усі послуги для ваших коліс та шин знаходяться тут.',
          options: {
            bringOwnTires: 'Заміна коліс - я привезу свої колеса',
            storedTires: 'Заміна коліс - мої колеса на зберіганні',
            pickupDelivery: 'Послуга доставки та забору',
            storeTires: 'Будь ласка, збережіть мої доставлені колеса'
          }
        },
        actionsChecks: {
          title: 'Акції / Перевірки',
          description: 'Хочете перевірити свій автомобіль або підготувати до поточного сезону? Замовте відповідну послугу тут.',
          options: {
            warrantyCheck: 'Безкоштовна перевірка перед закінченням гарантії виробника',
            safetyCheck: 'Перевірка безпеки / сезонна перевірка',
            fluidCheck: 'Перевірка рівня рідин (безкоштовно) та доливання за потреби',
            batteryCheck: 'Перевірка акумулятора (безкоштовно в мережі)'
          }
        },
        repair: {
          title: 'Ремонт / Скарга',
          description: 'Маєте інші питання щодо вашого автомобіля? Натисніть тут та вкажіть у полі коментарів.',
          options: {
            diagnostics: 'Загальна діагностика',
            noises: 'Шуми',
            acMalfunction: 'Несправність кондиціонера'
          }
        },
        bodywork: {
          title: 'Кузов / Заміна лобового скла',
          description: 'Потрібне нове лобове скло або кошторис? Швидко та легко запишіться тут.',
          options: {
            costEstimate: 'Кошторис / оцінка після аварії',
            windshieldReplacement: 'Заміна лобового скла'
          }
        }
      },
      appointment: {
        title: 'Оберіть зручний для вас день та час',
        calendarLink: 'Тут ви можете побачити більше вільних записів у нашому календарі майстерні',
        backButton: 'Назад',
        continueButton: 'Далі',
        ariaGroupLabel: 'Пропозиції записів',
        navAriaLabel: 'Навігація по сторінках'
      },
      workshopCalendar: {
        title: 'Тут ви можете побачити додаткові вільні записи у нашому календарі майстерні',
        description: 'Оберіть бажану дату. Ми покажемо вам усі доступні записи починаючи з цього дня.',
        desiredDateLabel: 'Ваша бажана дата:',
        datePlaceholder: 'Оберіть бажану дату',
        hintBefore: 'Оберіть бажану дату в календарі, і ми покажемо вам найближчі доступні записи',
        hintAfter: 'Ми знайшли наступні доступні записи від обраної вами дати. Натисніть на час, щоб обрати запис.',
        dateInputAriaLabel: 'Введіть бажану дату у форматі ДД.ММ.РРРР',
        slotsAriaLabel: 'Доступні часи',
        backButton: 'Назад',
        continueButton: 'Далі',
        navAriaLabel: 'Навігація по сторінках'
      },
      notes: {
        pageTitle: 'Будь ласка, надайте додаткові примітки до вашого запису',
        sectionTitle: 'Чи хочете ви повідомити нам щось ще про ваш запис?',
        textareaPlaceholder: 'Будь ласка, введіть тут ваше повідомлення.',
        textareaHint: 'Будь ласка, введіть тут ваше повідомлення.',
        charCountAriaLabel: '{current} з {max} символів використано',
        hintsTitle: 'Важливі примітки щодо обраних послуг',
        backButton: 'Назад',
        continueButton: 'Далі',
        mobilityOptions: {
          title: 'Варіанти мобільності (платно)',
          label: 'Обрати',
          none: 'Без вибору',
          compactCar: 'Малолітражка',
          midRange: 'Середній клас',
          luxury: 'Преміум-клас'
        },
        rentCategory: {
          title: 'Категорія орендного автомобіля',
          label: 'Обрати категорію',
          none: 'Без орендного автомобіля'
        },
        rentCategoryPrice: {
          label: 'Категорія орендного автомобіля',
          unitDay: 'на день',
          unitHour: 'за годину',
          srLabel: 'Категорію орендного автомобіля обрано'
        },
        appointmentPreference: {
          title: 'Бажаний час',
          label: 'Обрати',
          anytime: 'Будь-який час',
          morning: 'Зранку',
          afternoon: 'Після обіду'
        },
        callback: {
          title: 'Додаткові опції: Зворотний дзвінок',
          label: 'Зворотний дзвінок',
          no: 'Ні',
          yes: 'Так'
        },
        notification: {
          title: 'Сповіщення',
          label: 'Як ви хочете отримувати сповіщення?',
          email: 'Електронна пошта',
          sms: 'SMS'
        }
      },
      carinformation: {
        title: 'Будь ласка, надайте нам останню інформацію про ваш запис',
        returningCustomer: {
          title: 'Вже бували у нас?',
          description: 'Отримайте свої дані автоматично, ввівши свою електронну адресу.',
          button: 'Отримати мої дані!'
        },
        form: {
          customerTitle: 'Ваші дані',
          vehicleTitle: 'Дані автомобіля',
          email: { label: 'Електронна адреса', placeholder: 'ivan@example.com',
            error: { required: 'Будь ласка, введіть свою електронну адресу.', invalid: 'Будь ласка, введіть дійсну електронну адресу.' } },
          salutation: { label: 'Звертання', placeholder: 'Будь ласка, оберіть', mr: 'Пан', ms: 'Пані',
            error: { required: 'Будь ласка, оберіть звертання.' } },
          firstName: { label: 'Ім\'я', placeholder: 'Іван',
            error: { required: 'Будь ласка, введіть своє ім\'я.', lettersOnly: 'Ім\'я може містити лише літери.' } },
          lastName: { label: 'Прізвище', placeholder: 'Іваненко',
            error: { required: 'Будь ласка, введіть своє прізвище.', lettersOnly: 'Прізвище може містити лише літери.' } },
          street: { label: 'Вулиця та номер будинку', placeholder: 'вул. Прикладна, 1',
            error: { required: 'Будь ласка, введіть вулицю та номер будинку.' } },
          postalCode: { label: 'Поштовий індекс', placeholder: '30159',
            error: { required: 'Будь ласка, введіть поштовий індекс.', digitsOnly: 'Поштовий індекс може містити лише цифри.' } },
          city: { label: 'Місто', placeholder: 'Берлін',
            error: { required: 'Будь ласка, введіть місто.', lettersOnly: 'Місто може містити лише літери.' } },
          mobilePhone: { label: 'Мобільний телефон', placeholder: '017012345678',
            hint: 'Будь ласка, введіть номер без спеціальних символів у форматі 017012345678.',
            error: { required: 'Будь ласка, введіть номер мобільного телефону.', digitsOnly: 'Номер може містити лише цифри.', startsWithZero: 'Номер мобільного телефону має починатися з 0.' } },
          licensePlate: { label: 'Номерний знак', placeholder: 'XX-XX1234',
            error: { required: 'Будь ласка, введіть номерний знак.', invalidFormat: 'Будь ласка, введіть дійсний номерний знак (напр. B-MS1234).' } },
          mileage: { label: 'Пробіг', placeholder: '5000',
            error: { required: 'Будь ласка, введіть пробіг.', digitsOnly: 'Пробіг може містити лише цифри.' } },
vin: { label: 'VIN', placeholder: 'WDB8XXXXXXA123456', infoLink: 'Пояснення VIN',
            error: { required: 'Будь ласка, введіть ідентифікаційний номер автомобіля.', invalidLength: 'VIN має складатися рівно з 17 символів.', invalidFormat: 'VIN може містити лише літери та цифри.' } },
          privacy: { consent: 'Я погоджуюся на обробку моїх персональних даних з метою онлайн-запису на технічне обслуговування. Детальніше в нашій',
            privacyLink: 'Політиці конфіденційності',
            error: { required: 'Будь ласка, підтвердьте політику конфіденційності.' } },
          requiredHint: 'Обов\'язкові поля позначені *'
        },
        navigation: { back: 'Назад', continue: 'До підсумку бронювання' }
      },
      bookingOverview: {
        title: 'Огляд',
        subtitle: 'Будь ласка, перевірте свої дані перед відправленням запису.',
        ariaGridLabel: 'Огляд бронювання',
        ariaNavLabel: 'Навігація сторінки',
        edit: 'Редагувати',
        tiles: {
          appointment: {
            title: 'Бажаний термін',
            dateLabel: 'Дата',
            timeLabel: 'Час'
          },
          services: {
            title: 'Обраний сервіс',
            intro: 'Наступні послуги будуть запитані в',
            introSuffix: 'для вас:',
            empty: 'Послуги не вибрано.'
          },
          personalData: {
            title: 'Ваші дані',
            nameLabel: "Ім'я",
            streetLabel: 'Вулиця',
            cityLabel: 'Місто',
            phoneLabel: 'Телефон',
            emailLabel: 'Електронна пошта',
            brandLabel: 'Марка',
            licensePlateLabel: 'Номерний знак',
            mileageLabel: 'Пробіг',
            mileageUnit: 'км'
          },
          price: {
            title: 'Ціна',
            vatIncluded: 'вкл. ПДВ',
            staticPrice: '€ 129,00'
          }
        },
        navigation: {
          back: 'Назад',
          submit: 'Запросити зараз',
          submitting: 'Надсилається...',
          submitted: 'Запит надіслано'
        },
        confirmation: {
          message: 'Дякуємо, ваш запис заброньовано. Ми з нетерпінням чекаємо на ваш візит.'
        }
      },
      api: {
        loading: 'Завантаження даних...',
        submitting: 'Відправлення вашого запиту...',
        errorTitle: 'Помилка завантаження',
        errorServerError: 'Сервер наразі недоступний. Будь ласка, спробуйте пізніше.',
        errorTimeout: 'Запит тривав занадто довго. Будь ласка, спробуйте знову.',
        errorTokenFailed: 'Не вдалося встановити сеанс. Будь ласка, перезавантажте сторінку.',
        errorUnknown: 'Сталася невідома помилка. Будь ласка, спробуйте знову.',
        errorSubmit: 'Не вдалося надіслати бронювання. Будь ласка, спробуйте знову.',
        retryButton: 'Повторити',
        fallbackAddress: 'Musterstraße 1, 00000 Musterhausen'
      },
      overlay: {
        cartLabel: 'Кошик',
        accessibilityLabel: 'Доступність',
        cartBadgeLabel: '{count} товарів у кошику',
        selectionTitle: 'Ваш вибір',
        locationLabel: 'Місцезнаходження',
        brandLabel: 'Марка',
        servicesLabel: 'Послуги',
        appointmentLabel: 'Запис',
        emptySelection: 'Ще нічого не обрано.'
      }
    }
  },

  fr: {
    app: {
      title: 'Autohaus GmbH',
      subtitle: 'Fahrzeugauswahl',
      skipLink: 'Aller au contenu principal',
      error: {
        title: 'Une erreur s\'est produite',
        subtext: 'Nous ne pouvons pas charger les données pour le moment. Veuillez réessayer ou revenir plus tard.',
        offlineHint: 'Veuillez vérifier votre connexion Internet.',
        retryButton: 'Réessayer',
        retryButtonAriaLabel: 'Réessayer de charger les données',
        detailsToggle: 'Afficher les détails techniques',
        codeLabel: 'Code d\'erreur',
        statusLabel: 'Statut',
        timestampLabel: 'Horodatage',
        requestIdLabel: 'ID de requête'
      }
    },

    header: {
      accessibility: {
        button: 'Accessibilité',
        buttonLabel: 'Paramètres d\'accessibilité',
        fontSize: {
          label: 'Taille de police',
          small: 'Petite',
          normal: 'Normale',
          large: 'Grande',
          xLarge: 'Très grande'
        },
        highContrast: 'Contraste élevé',
        reducedMotion: 'Animation réduite',
        language: {
          label: 'Langue'
        }
      },
      logo: {
        alt: 'Logo de l\'entreprise'
      },
      close: {
        buttonLabel: 'Fermer la réservation et retourner au site'
      },
      cart: {
        button: 'Panier',
        title: 'Panier',
        empty: 'Votre panier est vide',
        placeholder: 'Contenu bientôt disponible',
        summary: 'Vous avez sélectionné {count} service(s)',
        brandLabel: 'Marque',
        locationLabel: 'Emplacement',
        servicesLabel: 'Services',
        appointmentLabel: 'Rendez-vous',
        badge: {
          ariaLabel: 'articles dans le panier'
        }
      }
    },

    booking: {
      wizard: {
        location: 'Lieu',
        brand: 'Marque',
        service: 'Service',
        notes: 'Remarques',
        appointment: 'Rendez-vous',
        vehicle: 'Véhicule',
        overview: 'Aperçu'
      },
      branch: {
        infoLabel: 'Votre lieu',
        infoSeparator: '—'
      },
      brand: {
        title: 'Quelle marque souhaitez-vous faire entretenir ?',
        subtitle: 'Les marques suivantes sont disponibles à votre emplacement',
        backButton: 'Retour',
        noBrandsAvailable: 'Aucune marque n\'est disponible pour cet emplacement.',
        ariaGroupLabel: 'Sélection de la marque',
        ariaNavLabel: 'Navigation de la page'
      },
      location: {
        title: 'Réservez en ligne votre visite à l\'atelier !',
        subtitle: 'Sélectionnez votre emplacement — les logos des marques vous aident à vous orienter',
        ariaGroupLabel: 'Sélection de l\'emplacement',
        ariaNavLabel: 'Navigation de la page',
        continueButton: 'Continuer'
      },
      services: {
        title: 'Quels services souhaitez-vous réserver ?',
        subtitle: 'Sélectionnez les services souhaités.',
        ariaGroupLabel: 'Services disponibles',
        continueButton: 'Continuer',
        backButton: 'Retour',
        confirmButton: 'Confirmer',
        deselectButton: 'Désélectionner',
        inspection: {
          title: 'Inspection',
          description: 'Services pour votre véhicule : de l\'inspection à la vidange en passant par l\'alignement.',
          options: {
            dialogAcceptance: 'Accueil dialogue',
            inspection: 'Inspection',
            oilChange: 'Service vidange',
            brakeCheck: 'Vérification des freins (gratuit) et remplacement si nécessaire',
            wiperBlades: 'Remplacement des essuie-glaces',
            maintenanceContract: 'Intérêt pour un contrat d\'entretien',
            emergencyOil: '1 litre d\'huile de secours'
          }
        },
        tuv: {
          title: 'Contrôle technique',
          description: 'Cliquez ici pour prendre rendez-vous pour le contrôle technique ou la vérification UVV.',
          options: {
            tuv: 'Contrôle technique',
            uvvInspection: 'Vérification UVV (véhicules de service et commerciaux)',
            acCleaning: 'Nettoyage de la climatisation (149,00 € TTC)'
          }
        },
        brakeFluid: {
          title: 'Changement de liquide de frein',
          description: 'Le changement de liquide de frein de votre véhicule est dû ? Réservez simplement ici.'
        },
        tireChange: {
          title: 'Changement de pneus',
          description: 'Tous les services pour vos roues et pneus sont disponibles ici.',
          options: {
            bringOwnTires: 'Changement de pneus - j\'apporte mes propres roues',
            storedTires: 'Changement de pneus - mes roues sont entreposées',
            pickupDelivery: 'Service de collecte et livraison',
            storeTires: 'Veuillez entreposer mes roues livrées'
          }
        },
        actionsChecks: {
          title: 'Actions / Vérifications',
          description: 'Vous souhaitez faire vérifier votre véhicule ou le préparer pour la saison ? Réservez le bon service ici.',
          options: {
            warrantyCheck: 'Vérification gratuite avant expiration de la garantie',
            safetyCheck: 'Vérification de sécurité / saisonnière',
            fluidCheck: 'Vérification des niveaux de liquides (gratuit) et complément si nécessaire',
            batteryCheck: 'Vérification de la batterie (gratuit en réseau)'
          }
        },
        repair: {
          title: 'Réparation / Réclamation',
          description: 'Vous avez d\'autres préoccupations concernant votre véhicule ? Cliquez ici et précisez dans le champ de commentaire.',
          options: {
            diagnostics: 'Diagnostic général',
            noises: 'Bruits',
            acMalfunction: 'Climatisation en panne'
          }
        },
        bodywork: {
          title: 'Carrosserie / Remplacement de pare-brise',
          description: 'Besoin d\'un nouveau pare-brise ou d\'un devis ? Réservez rapidement et facilement ici.',
          options: {
            costEstimate: 'Devis / expertise après accident',
            windshieldReplacement: 'Remplacement du pare-brise'
          }
        }
      },
      appointment: {
        title: 'Sélectionnez le jour et l\'heure qui vous conviennent',
        calendarLink: 'Consultez ici d\'autres créneaux disponibles dans notre calendrier d\'atelier',
        backButton: 'Retour',
        continueButton: 'Continuer',
        ariaGroupLabel: 'Propositions de rendez-vous',
        navAriaLabel: 'Navigation par page'
      },
      workshopCalendar: {
        title: 'Consultez ici les créneaux disponibles dans notre calendrier d\'atelier',
        description: 'Sélectionnez votre date souhaitée. Nous vous montrerons tous les rendez-vous disponibles à partir de ce jour.',
        desiredDateLabel: 'Votre date souhaitée :',
        datePlaceholder: 'Sélectionner une date',
        hintBefore: 'Sélectionnez une date dans le calendrier et nous vous montrerons les prochains rendez-vous disponibles',
        hintAfter: 'Nous avons trouvé les rendez-vous disponibles suivants à partir de votre date sélectionnée. Cliquez sur un horaire pour sélectionner le rendez-vous.',
        dateInputAriaLabel: 'Entrez la date souhaitée au format JJ.MM.AAAA',
        slotsAriaLabel: 'Horaires disponibles',
        backButton: 'Retour',
        continueButton: 'Continuer',
        navAriaLabel: 'Navigation par page'
      },
      notes: {
        pageTitle: 'Veuillez fournir des remarques supplémentaires pour votre réservation',
        sectionTitle: 'Souhaitez-vous nous communiquer autre chose concernant votre réservation ?',
        textareaPlaceholder: 'Veuillez saisir votre message ici.',
        textareaHint: 'Veuillez saisir votre message ici.',
        charCountAriaLabel: '{current} sur {max} caractères utilisés',
        hintsTitle: 'Remarques importantes concernant vos services sélectionnés',
        backButton: 'Retour',
        continueButton: 'Continuer',
        mobilityOptions: {
          title: 'Options de mobilité (payantes)',
          label: 'Sélectionner',
          none: 'Aucune sélection',
          compactCar: 'Voiture compacte',
          midRange: 'Gamme moyenne',
          luxury: 'Haut de gamme'
        },
        rentCategoryPrice: {
          label: 'Catégorie de voiture de location',
          unitDay: 'par jour',
          unitHour: 'par heure',
          srLabel: 'Catégorie de voiture de location sélectionnée'
        },
        rentCategory: {
          title: 'Catégorie de voiture de location',
          label: 'Choisir une catégorie',
          none: 'Pas de voiture de location'
        },
        appointmentPreference: {
          title: 'Préférence de rendez-vous',
          label: 'Sélectionner',
          anytime: 'À tout moment',
          morning: 'Le matin',
          afternoon: "L'après-midi"
        },
        callback: {
          title: 'Options supplémentaires : Rappel',
          label: 'Rappel',
          no: 'Non',
          yes: 'Oui'
        },
        notification: {
          title: 'Notification',
          label: 'Comment souhaitez-vous être notifié ?',
          email: 'E-mail',
          sms: 'SMS'
        }
      },
      carinformation: {
        title: 'Veuillez nous fournir les dernières informations sur votre rendez-vous',
        returningCustomer: {
          title: 'Déjà venu chez nous ?',
          description: 'Récupérez vos données automatiquement en saisissant votre adresse e-mail.',
          button: 'Récupérer mes données !'
        },
        form: {
          customerTitle: 'Vos coordonnées',
          vehicleTitle: 'Données du véhicule',
          email: { label: 'Adresse e-mail', placeholder: 'max@exemple.fr',
            error: { required: 'Veuillez saisir votre adresse e-mail.', invalid: 'Veuillez saisir une adresse e-mail valide.' } },
          salutation: { label: 'Civilité', placeholder: 'Veuillez sélectionner', mr: 'M.', ms: 'Mme',
            error: { required: 'Veuillez sélectionner une civilité.' } },
          firstName: { label: 'Prénom', placeholder: 'Jean',
            error: { required: 'Veuillez saisir votre prénom.', lettersOnly: 'Le prénom ne peut contenir que des lettres.' } },
          lastName: { label: 'Nom de famille', placeholder: 'Dupont',
            error: { required: 'Veuillez saisir votre nom de famille.', lettersOnly: 'Le nom ne peut contenir que des lettres.' } },
          street: { label: 'Rue et numéro', placeholder: 'Rue Exemple 1',
            error: { required: 'Veuillez saisir votre rue et numéro.' } },
          postalCode: { label: 'Code postal', placeholder: '75001',
            error: { required: 'Veuillez saisir votre code postal.', digitsOnly: 'Le code postal ne peut contenir que des chiffres.' } },
          city: { label: 'Ville', placeholder: 'Paris',
            error: { required: 'Veuillez saisir votre ville.', lettersOnly: 'La ville ne peut contenir que des lettres.' } },
          mobilePhone: { label: 'Téléphone mobile', placeholder: '017012345678',
            hint: 'Veuillez saisir votre numéro sans caractères spéciaux au format 017012345678.',
            error: { required: 'Veuillez saisir votre numéro de téléphone mobile.', digitsOnly: 'Le numéro ne peut contenir que des chiffres.', startsWithZero: 'Le numéro de mobile doit commencer par 0.' } },
          licensePlate: { label: 'Plaque d\'immatriculation', placeholder: 'XX-XX1234',
            error: { required: 'Veuillez saisir votre plaque d\'immatriculation.', invalidFormat: 'Veuillez saisir une plaque valide (ex. B-MS1234).' } },
          mileage: { label: 'Kilométrage', placeholder: '5000',
            error: { required: 'Veuillez saisir le kilométrage.', digitsOnly: 'Le kilométrage ne peut contenir que des chiffres.' } },
vin: { label: 'NIV', placeholder: 'WDB8XXXXXXA123456', infoLink: 'Explication du NIV',
            error: { required: 'Veuillez saisir le numéro d\'identification du véhicule.', invalidLength: 'Le NIV doit comporter exactement 17 caractères.', invalidFormat: 'Le NIV ne peut contenir que des lettres et des chiffres.' } },
          privacy: { consent: 'Je consens au traitement de mes données personnelles aux fins de la prise de rendez-vous en ligne. Pour en savoir plus, consultez notre',
            privacyLink: 'Politique de confidentialité',
            error: { required: 'Veuillez accepter la politique de confidentialité.' } },
          requiredHint: 'Les champs obligatoires sont marqués d\'un *'
        },
        navigation: { back: 'Retour', continue: 'Vers le récapitulatif' }
      },
      bookingOverview: {
        title: 'Récapitulatif',
        subtitle: 'Veuillez vérifier vos informations avant d\'envoyer le rendez-vous.',
        ariaGridLabel: 'Récapitulatif de la réservation',
        ariaNavLabel: 'Navigation de la page',
        edit: 'Modifier',
        tiles: {
          appointment: {
            title: 'Rendez-vous souhaité',
            dateLabel: 'Date',
            timeLabel: 'Heure'
          },
          services: {
            title: 'Service sélectionné',
            intro: 'Les prestations suivantes seront demandées à',
            introSuffix: 'pour vous :',
            empty: 'Aucune prestation sélectionnée.'
          },
          personalData: {
            title: 'Vos données',
            nameLabel: 'Nom',
            streetLabel: 'Rue',
            cityLabel: 'Ville',
            phoneLabel: 'Téléphone',
            emailLabel: 'E-mail',
            brandLabel: 'Marque',
            licensePlateLabel: 'Plaque d\'immatriculation',
            mileageLabel: 'Kilométrage',
            mileageUnit: 'km'
          },
          price: {
            title: 'Prix',
            vatIncluded: 'TTC',
            staticPrice: '129,00 €'
          }
        },
        navigation: {
          back: 'Retour',
          submit: 'Demander maintenant',
          submitting: 'Envoi en cours...',
          submitted: 'Demande envoyée'
        },
        confirmation: {
          message: 'Merci, votre rendez-vous est réservé. Nous nous réjouissons de votre visite.'
        }
      },
      api: {
        loading: 'Chargement des données...',
        submitting: 'Envoi de votre demande en cours...',
        errorTitle: 'Erreur de chargement',
        errorServerError: 'Le serveur est actuellement indisponible. Veuillez réessayer plus tard.',
        errorTimeout: 'La requête a pris trop de temps. Veuillez réessayer.',
        errorTokenFailed: 'La session n\'a pas pu être établie. Veuillez recharger la page.',
        errorUnknown: 'Une erreur inconnue s\'est produite. Veuillez réessayer.',
        errorSubmit: 'La réservation n\'a pas pu être envoyée. Veuillez réessayer.',
        retryButton: 'Réessayer',
        fallbackAddress: 'Musterstraße 1, 00000 Musterhausen'
      },
      overlay: {
        cartLabel: 'Panier',
        accessibilityLabel: 'Accessibilité',
        cartBadgeLabel: '{count} articles dans le panier',
        selectionTitle: 'Votre sélection',
        locationLabel: 'Emplacement',
        brandLabel: 'Marque',
        servicesLabel: 'Services',
        appointmentLabel: 'Rendez-vous',
        emptySelection: 'Aucune sélection effectuée.'
      }
    }
  },

  ar: {
    app: {
      title: 'Autohaus GmbH',
      subtitle: 'Fahrzeugauswahl',
      skipLink: 'انتقل إلى المحتوى الرئيسي',
      error: {
        title: 'حدث خطأ',
        subtext: 'لا يمكننا تحميل البيانات في الوقت الحالي. يرجى المحاولة مرة أخرى أو العودة لاحقاً.',
        offlineHint: 'يرجى التحقق من اتصالك بالإنترنت.',
        retryButton: 'إعادة المحاولة',
        retryButtonAriaLabel: 'إعادة محاولة تحميل البيانات',
        detailsToggle: 'عرض التفاصيل الفنية',
        codeLabel: 'رمز الخطأ',
        statusLabel: 'الحالة',
        timestampLabel: 'الطابع الزمني',
        requestIdLabel: 'معرف الطلب'
      }
    },

    header: {
      accessibility: {
        button: 'إمكانية الوصول',
        buttonLabel: 'إعدادات إمكانية الوصول',
        fontSize: {
          label: 'حجم الخط',
          small: 'صغير',
          normal: 'عادي',
          large: 'كبير',
          xLarge: 'كبير جداً'
        },
        highContrast: 'تباين عالٍ',
        reducedMotion: 'تقليل الحركة',
        language: {
          label: 'اللغة'
        }
      },
      logo: {
        alt: 'شعار الشركة'
      },
      close: {
        buttonLabel: 'إغلاق الحجز والعودة إلى الموقع'
      },
      cart: {
        button: 'سلة التسوق',
        title: 'سلة التسوق',
        empty: 'سلة التسوق فارغة',
        placeholder: 'المحتوى سيكون متاحاً قريباً',
        summary: 'لقد اخترت {count} خدمة/خدمات',
        brandLabel: 'العلامة التجارية',
        locationLabel: 'الموقع',
        servicesLabel: 'الخدمات',
        appointmentLabel: 'الموعد',
        badge: {
          ariaLabel: 'عناصر في سلة التسوق'
        }
      }
    },

    booking: {
      wizard: {
        location: 'الموقع',
        brand: 'العلامة',
        service: 'الخدمة',
        notes: 'ملاحظات',
        appointment: 'الموعد',
        vehicle: 'السيارة',
        overview: 'نظرة عامة'
      },
      branch: {
        infoLabel: 'موقعك',
        infoSeparator: '—'
      },
      brand: {
        title: 'أي علامة تجارية ترغب في صيانتها؟',
        subtitle: 'العلامات التجارية التالية متاحة في موقعك',
        backButton: 'رجوع',
        noBrandsAvailable: 'لا توجد علامات تجارية متاحة حالياً لهذا الموقع.',
        ariaGroupLabel: 'اختيار العلامة التجارية',
        ariaNavLabel: 'التنقل في الصفحة'
      },
      location: {
        title: 'احجز زيارتك لورشة العمل عبر الإنترنت!',
        subtitle: 'اختر موقعك — شعارات العلامات التجارية تساعدك في التنقل',
        ariaGroupLabel: 'اختيار الموقع',
        ariaNavLabel: 'التنقل في الصفحة',
        continueButton: 'متابعة'
      },
      services: {
        title: 'ما هي الخدمات التي ترغب في حجزها؟',
        subtitle: 'اختر الخدمات المطلوبة.',
        ariaGroupLabel: 'الخدمات المتاحة',
        continueButton: 'التالي',
        backButton: 'رجوع',
        confirmButton: 'تأكيد',
        deselectButton: 'إلغاء الاختيار',
        inspection: {
          title: 'فحص',
          description: 'خدمات لسيارتك: من الفحص إلى تغيير الزيت ومحاذاة السيارة.',
          options: {
            dialogAcceptance: 'استقبال حواري',
            inspection: 'فحص',
            oilChange: 'خدمة تغيير الزيت',
            brakeCheck: 'فحص الفرامل (مجاني) والاستبدال عند الحاجة',
            wiperBlades: 'استبدال شفرات المساحات',
            maintenanceContract: 'اهتمام بعقد صيانة',
            emergencyOil: '١ لتر زيت طوارئ'
          }
        },
        tuv: {
          title: 'الفحص الفني',
          description: 'انقر هنا لحجز موعد للفحص الفني أو فحص UVV.',
          options: {
            tuv: 'الفحص الفني',
            uvvInspection: 'فحص UVV (للسيارات الخدمية والتجارية)',
            acCleaning: 'تنظيف المكيف (١٤٩٫٠٠ يورو شامل الضريبة)'
          }
        },
        brakeFluid: {
          title: 'تغيير سائل الفرامل',
          description: 'هل حان وقت تغيير سائل فرامل سيارتك؟ احجز ببساطة هنا.'
        },
        tireChange: {
          title: 'تغيير الإطارات',
          description: 'جميع خدمات العجلات والإطارات متوفرة هنا.',
          options: {
            bringOwnTires: 'تغيير الإطارات - أحضر إطاراتي الخاصة',
            storedTires: 'تغيير الإطارات - إطاراتي مخزنة',
            pickupDelivery: 'خدمة الاستلام والتوصيل',
            storeTires: 'يرجى تخزين إطاراتي المُسلَّمة'
          }
        },
        actionsChecks: {
          title: 'عروض / فحوصات',
          description: 'هل تريد فحص سيارتك أو تجهيزها للموسم الحالي؟ احجز الخدمة المناسبة هنا.',
          options: {
            warrantyCheck: 'فحص مجاني قبل انتهاء ضمان الشركة المصنعة',
            safetyCheck: 'فحص السلامة / الفحص الموسمي',
            fluidCheck: 'فحص مستويات السوائل (مجاني) والتعبئة عند الحاجة',
            batteryCheck: 'فحص البطارية (مجاني في الشبكة)'
          }
        },
        repair: {
          title: 'إصلاح / شكوى',
          description: 'لديك مخاوف أخرى بشأن سيارتك؟ انقر هنا وحدد في حقل التعليقات.',
          options: {
            diagnostics: 'تشخيص عام',
            noises: 'أصوات',
            acMalfunction: 'عطل في المكيف'
          }
        },
        bodywork: {
          title: 'هيكل السيارة / استبدال الزجاج الأمامي',
          description: 'تحتاج زجاجًا أماميًا جديدًا أو تقدير تكلفة؟ احجز بسرعة وسهولة هنا.',
          options: {
            costEstimate: 'تقدير التكلفة / التقييم بعد الحادث',
            windshieldReplacement: 'استبدال الزجاج الأمامي'
          }
        }
      },
      appointment: {
        title: 'اختر اليوم والوقت المناسبين لك',
        calendarLink: 'هنا يمكنك رؤية المزيد من المواعيد المتاحة في تقويم الورشة',
        backButton: 'رجوع',
        continueButton: 'متابعة',
        ariaGroupLabel: 'اقتراحات المواعيد',
        navAriaLabel: 'التنقل بين الصفحات'
      },
      workshopCalendar: {
        title: 'هنا يمكنك رؤية المزيد من المواعيد المتاحة في تقويم الورشة',
        description: 'اختر التاريخ المطلوب. سنعرض لك جميع المواعيد المتاحة بدءاً من هذا اليوم.',
        desiredDateLabel: 'التاريخ المطلوب:',
        datePlaceholder: 'اختر التاريخ المطلوب',
        hintBefore: 'اختر تاريخاً في التقويم وسنعرض لك المواعيد المتاحة القادمة',
        hintAfter: 'وجدنا المواعيد المتاحة التالية بدءاً من التاريخ الذي اخترته. انقر على وقت لاختيار الموعد.',
        dateInputAriaLabel: 'أدخل التاريخ المطلوب بالتنسيق يي.شش.سسسس',
        slotsAriaLabel: 'الأوقات المتاحة',
        backButton: 'رجوع',
        continueButton: 'متابعة',
        navAriaLabel: 'التنقل بين الصفحات'
      },
      notes: {
        pageTitle: 'يرجى تقديم ملاحظات إضافية لحجزك',
        sectionTitle: 'هل تود إخبارنا بشيء آخر بخصوص حجزك؟',
        textareaPlaceholder: 'يرجى إدخال رسالتك إلينا هنا.',
        textareaHint: 'يرجى إدخال رسالتك إلينا هنا.',
        charCountAriaLabel: '{current} من {max} حرف مستخدم',
        hintsTitle: 'ملاحظات مهمة حول الخدمات المختارة',
        backButton: 'رجوع',
        continueButton: 'متابعة',
        mobilityOptions: {
          title: 'خيارات التنقل (مقابل رسوم)',
          label: 'اختيار',
          none: 'بدون اختيار',
          compactCar: 'سيارة صغيرة',
          midRange: 'فئة متوسطة',
          luxury: 'فئة فاخرة'
        },
        rentCategory: {
          title: 'فئة سيارة الإيجار',
          label: 'اختر الفئة',
          none: 'بدون سيارة إيجار'
        },
        rentCategoryPrice: {
          label: 'فئة سيارة الإيجار',
          unitDay: 'في اليوم',
          unitHour: 'في الساعة',
          srLabel: 'تم اختيار فئة سيارة الإيجار'
        },
        appointmentPreference: {
          title: 'تفضيل الموعد',
          label: 'اختيار',
          anytime: 'أي وقت',
          morning: 'صباحاً',
          afternoon: 'بعد الظهر'
        },
        callback: {
          title: 'خيارات إضافية: معاودة الاتصال',
          label: 'معاودة الاتصال',
          no: 'لا',
          yes: 'نعم'
        },
        notification: {
          title: 'الإشعار',
          label: 'كيف ترغب في تلقي الإشعارات؟',
          email: 'بريد إلكتروني',
          sms: 'رسالة نصية'
        }
      },
      carinformation: {
        title: 'يرجى تزويدنا بالمعلومات الأخيرة حول موعدك',
        returningCustomer: {
          title: 'سبق أن زرتنا؟',
          description: 'استرجع بياناتك تلقائياً بإدخال عنوان بريدك الإلكتروني.',
          button: 'استرداد بياناتي!'
        },
        form: {
          customerTitle: 'بياناتك',
          vehicleTitle: 'بيانات السيارة',
          email: { label: 'عنوان البريد الإلكتروني', placeholder: 'ahmed@example.com',
            error: { required: 'يرجى إدخال عنوان بريدك الإلكتروني.', invalid: 'يرجى إدخال عنوان بريد إلكتروني صحيح.' } },
          salutation: { label: 'التحية', placeholder: 'يرجى الاختيار', mr: 'السيد', ms: 'السيدة',
            error: { required: 'يرجى اختيار التحية.' } },
          firstName: { label: 'الاسم الأول', placeholder: 'أحمد',
            error: { required: 'يرجى إدخال اسمك الأول.', lettersOnly: 'الاسم الأول يجب أن يحتوي على أحرف فقط.' } },
          lastName: { label: 'اسم العائلة', placeholder: 'محمد',
            error: { required: 'يرجى إدخال اسم عائلتك.', lettersOnly: 'اسم العائلة يجب أن يحتوي على أحرف فقط.' } },
          street: { label: 'الشارع ورقم المبنى', placeholder: 'شارع النموذج 1',
            error: { required: 'يرجى إدخال الشارع ورقم المبنى.' } },
          postalCode: { label: 'الرمز البريدي', placeholder: '30159',
            error: { required: 'يرجى إدخال الرمز البريدي.', digitsOnly: 'الرمز البريدي يجب أن يحتوي على أرقام فقط.' } },
          city: { label: 'المدينة', placeholder: 'برلين',
            error: { required: 'يرجى إدخال المدينة.', lettersOnly: 'المدينة يجب أن تحتوي على أحرف فقط.' } },
          mobilePhone: { label: 'رقم الهاتف المحمول', placeholder: '017012345678',
            hint: 'يرجى إدخال الرقم بدون أحرف خاصة بصيغة 017012345678.',
            error: { required: 'يرجى إدخال رقم هاتفك المحمول.', digitsOnly: 'الرقم يجب أن يحتوي على أرقام فقط.', startsWithZero: 'يجب أن يبدأ رقم الهاتف المحمول بـ 0.' } },
          licensePlate: { label: 'لوحة الترخيص', placeholder: 'XX-XX1234',
            error: { required: 'يرجى إدخال لوحة الترخيص.', invalidFormat: 'يرجى إدخال لوحة ترخيص صحيحة (مثال: B-MS1234).' } },
          mileage: { label: 'عداد المسافات', placeholder: '5000',
            error: { required: 'يرجى إدخال قراءة عداد المسافات.', digitsOnly: 'عداد المسافات يجب أن يحتوي على أرقام فقط.' } },
vin: { label: 'VIN', placeholder: 'WDB8XXXXXXA123456', infoLink: 'شرح رقم VIN',
            error: { required: 'يرجى إدخال رقم تعريف السيارة.', invalidLength: 'يجب أن يتكون رقم VIN من 17 حرفاً بالضبط.', invalidFormat: 'رقم VIN يجب أن يحتوي على أحرف وأرقام فقط.' } },
          privacy: { consent: 'أوافق على معالجة بياناتي الشخصية لغرض حجز موعد عبر الإنترنت. لمزيد من المعلومات راجع',
            privacyLink: 'سياسة الخصوصية',
            error: { required: 'يرجى قبول سياسة الخصوصية.' } },
          requiredHint: 'الحقول المطلوبة محددة بـ *'
        },
        navigation: { back: 'رجوع', continue: 'إلى ملخص الحجز' }
      },
      bookingOverview: {
        title: 'ملخص',
        subtitle: 'يرجى مراجعة بياناتك قبل إرسال الموعد.',
        ariaGridLabel: 'ملخص الحجز',
        ariaNavLabel: 'التنقل في الصفحة',
        edit: 'تعديل',
        tiles: {
          appointment: {
            title: 'الموعد المطلوب',
            dateLabel: 'التاريخ',
            timeLabel: 'الوقت'
          },
          services: {
            title: 'الخدمة المختارة',
            intro: 'سيتم طلب الخدمات التالية في',
            introSuffix: 'لك:',
            empty: 'لم يتم تحديد أي خدمات.'
          },
          personalData: {
            title: 'بياناتك',
            nameLabel: 'الاسم',
            streetLabel: 'الشارع',
            cityLabel: 'المدينة',
            phoneLabel: 'الهاتف',
            emailLabel: 'البريد الإلكتروني',
            brandLabel: 'العلامة التجارية',
            licensePlateLabel: 'لوحة الترخيص',
            mileageLabel: 'عدد الكيلومترات',
            mileageUnit: 'كم'
          },
          price: {
            title: 'السعر',
            vatIncluded: 'شامل ضريبة القيمة المضافة',
            staticPrice: '€ 129,00'
          }
        },
        navigation: {
          back: 'رجوع',
          submit: 'اطلب الآن',
          submitting: 'جارٍ الإرسال...',
          submitted: 'تم إرسال الطلب'
        },
        confirmation: {
          message: 'شكرًا لك، تم حجز موعدك. نتطلع إلى زيارتك.'
        }
      },
      api: {
        loading: 'جارٍ تحميل البيانات...',
        submitting: 'جارٍ إرسال طلبك...',
        errorTitle: 'خطأ في التحميل',
        errorServerError: 'الخادم غير متاح حالياً. يرجى المحاولة مرة أخرى لاحقاً.',
        errorTimeout: 'استغرق الطلب وقتاً طويلاً. يرجى المحاولة مرة أخرى.',
        errorTokenFailed: 'تعذر إنشاء الجلسة. يرجى إعادة تحميل الصفحة.',
        errorUnknown: 'حدث خطأ غير معروف. يرجى المحاولة مرة أخرى.',
        errorSubmit: 'تعذر إرسال الحجز. يرجى المحاولة مرة أخرى.',
        retryButton: 'إعادة المحاولة',
        fallbackAddress: 'Musterstraße 1, 00000 Musterhausen'
      },
      overlay: {
        cartLabel: 'سلة التسوق',
        accessibilityLabel: 'إمكانية الوصول',
        cartBadgeLabel: '{count} عناصر في السلة',
        selectionTitle: 'اختيارك',
        locationLabel: 'الموقع',
        brandLabel: 'العلامة التجارية',
        servicesLabel: 'الخدمات',
        appointmentLabel: 'الموعد',
        emptySelection: 'لم يتم الاختيار بعد.'
      }
    }
  }
} as const;

// Helper types for nested keys
type NestedKeyOf<T> = T extends object
  ? {
      [K in keyof T]: K extends string
        ? T[K] extends object
          ? `${K}` | `${K}.${NestedKeyOf<T[K]>}`
          : `${K}`
        : never;
    }[keyof T]
  : never;

export type TranslationKey = NestedKeyOf<typeof translations.de>;
export type Language = keyof typeof translations;

/**
 * Language display configuration
 * Each language shown in its native name with flag emoji
 */
export interface LanguageOption {
  code: Language;
  label: string;
}

export const AVAILABLE_LANGUAGES: LanguageOption[] = [
  { code: 'de', label: 'Deutsch' }
  // EN-Auswahl entfernt — Translation-Daten unten bleiben erhalten,
  // koennen via 1-Zeilen-Change wieder aktiviert werden.
];

/**
 * Key tree: Mirrors the translation structure, but leaf values are dot-separated key paths
 * Enables object-oriented access: i18nKeys.header.warenkorb.button → 'header.warenkorb.button'
 */
type KeyTree<T, P extends string = ''> = {
  readonly [K in keyof T]: T[K] extends Record<string, unknown>
    ? KeyTree<T[K], P extends '' ? K & string : `${P}.${K & string}`>
    : P extends '' ? K & string : `${P}.${K & string}`;
};

function createKeyTree<T extends Record<string, unknown>>(
  obj: T, prefix = ''
): KeyTree<T> {
  const result = {} as Record<string, unknown>;
  for (const key of Object.keys(obj)) {
    const path = prefix ? `${prefix}.${key}` : key;
    const value = obj[key];
    if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
      result[key] = createKeyTree(value as Record<string, unknown>, path);
    } else {
      result[key] = path;
    }
  }
  return result as KeyTree<T>;
}

/** Object-oriented key access: i18nKeys.header.warenkorb.button → 'header.warenkorb.button' */
export const i18nKeys = createKeyTree(translations.de);
