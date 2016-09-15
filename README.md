ADS-B Flight Scanner
====================

Do you have an Android device that you would like to put to use? You can now set up and run your own
mobile ADS-B ground station that can be used anywhere and receive real-time data directly from
airplanes on your Android device. 

It receives ADS-B data directly from airplane transponders via a small antenna connected to your
Android device. This app then makes the real-time data available to view on your Android's screen, or 
optionally to any user on your local network if they point their web browser to your Android device's IP address.

Your mobile ADS-B station can run this Android app to track flights within 50-300 miles (line of sight,
range depending on antenna).


Compilation Notes
-----------------
This project is intended to be compiled with Android Studio 2.1 or higher.
https://developer.android.com/studio/

No special build instructions are required, and all other code dependencies are already included.

Configuration files to support use of ProGuard to shrink the compiled APK as small as possible is
also included, but this can be disabled if necessary or it is problematic.


Confirm your Android supports USB OTG
-------------------------------------

USB OTG is a requirement to run this app, however, most new devices released in the last couple of years are compatible with USB OTG.

* Download and install OTG Troubleshooter on your Android device. http://play.google.com/store/apps/details?id=com.homesoft.otgtroubshooter
* Start the OTG Troubleshooter app.
* Verify that the first field shows a green checkmark next to USB Host Support
* Plug in the OTG cable/dock to your Android device.
* The field that says OTG detected will have checkmark next to it if your device supports USB OTG.
* Uninstall OTG Troubleshooter app once you have verified support.



USB OTG cable/dock
------------------

You must have a way to connect the USB-RTL dongle to your Android device. This is typically done using
a USB On-The-Go (OTG) cable or docking station that is compatible with your Android device.

Most USB OTG Cable/Docks do not support simultaneous charging or have limited Android model compatibility.
You might need to purchase a different USB OTG Cable/Dock that is compatible with your Android device.

We strongly recommend that you use a OTG cable/dock that allows your phone to be charged at the same
time because tracking flights will fully drain the battery on your device very quickly (usually in
less than an hour). 

The following USB OTG cables or docks also support simultaneous Android charging for some devices:

* ZhiZhu OTG Charger Dock for Samsung Galaxy S5 S4 S3 Note 4 3 2 http://www.amazon.com/ZhiZhu-Charger-Charge-Charging-Samsung/dp/B00OTHNLUQ?tag=fligh01-20
* Valarm Micro USB Host OTG Y-Cable with Micro USB Power Charging for Samsung Phones - Supports charging for some Galaxy devices http://shop.valarm.net/products/micro-usb-host-otg-y-cable-with-micro-usb-power-for-samsung
* Micro USB Host OTG Cable with Micro USB Power - NOTE: This model does not supply power to the phone itself http://www.amazon.com/dp/B00CXAC1ZW?tag=fligh01-20


If your Android phone/tablet supports wireless (Qi) recharging, then that may be another way to power your
device and simultaneously use the USB port for connecting to the DVB-T dongle, if you are unable to find a
compatible USB OTG cable that also supports supplying power. This method will require having a non-powered
USB OTG cable, a wireless charging dock, and a wireless power receiver if your device did not originally
come with it.




RTL-SDR Dongle
--------------

You must also purchase an ADS-B (DVB-T) Receiver USB Dongle with Antenna.

List of supported USB RTL-SDR dongles:

* Generic RTL2832U
* Generic RTL2832U OEM
* FlightAware Pro Stick -- https://flightaware.com/adsb/prostick/
* DigitalNow Quad DVB-T PCI-E card
* Leadtek WinFast DTV Dongle mini D
* Genius TVGo DVB-T03 USB dongle (Ver. B)
* Terratec Cinergy T Stick Black (rev 1)
* Terratec NOXON DAB/DAB+ USB dongle (rev 1)
* Terratec Deutschlandradio DAB Stick
* Terratec NOXON DAB Stick - Radio Energy
* Terratec Media Broadcast DAB Stick
* Terratec BR DAB Stick
* Terratec WDR DAB Stick
* Terratec MuellerVerlag DAB Stick
* Terratec Fraunhofer DAB Stick
* Terratec Cinergy T Stick RC (Rev.3)
* Terratec T Stick PLUS
* Terratec NOXON DAB/DAB+ USB dongle (rev 2)
* PixelView PV-DT235U(RN)
* Astrometa DVB-T/DVB-T2
* Compro Videomate U620F
* Compro Videomate U650F
* Compro Videomate U680F
* GIGABYTE GT-U7300
* DIKOM USB-DVBT HD
* Peak 102569AGPK
* KWorld KW-UB450-T USB DVB-T Pico TV
* Zaapa ZT-MINDVBZP
* SVEON STV20 DVB-T USB & FM
* Twintech UT-40
* ASUS U3100MINI_PLUS_V2
* SVEON STV27 DVB-T USB & FM
* SVEON STV21 DVB-T USB & FM
* Dexatek DK DVB-T Dongle (Logilink VG0002A)
* Dexatek DK DVB-T Dongle (MSI DigiVox mini II V3.0)
* Dexatek Technology Ltd. DK 5217 DVB-T Dongle
* MSI DigiVox Micro HD
* Sweex DVB-T USB
* GTek T803
* Lifeview LV5TDeluxe
* MyGica TD312
* PROlectrix DV107669




App History
-----------
This app was originally developed by FlightAware in October 2014, as an experiment to see if there was
community interest in using Android hardware to collect ADS-B aircraft data. Its implementation was
based loosely off of several existing projects, notably by translating portions of dump1090 and dump978 to Java.

The versions of this app initially published by FlightAware were called "FlightFeeder for Android"
and included networking capability to upload received aircraft positions to the FlightAware ADS-B network.

Ultimately, it was found that only a very small number of users were willing to dedicate old Android
devices to running the app on a regular basis, and that it was easier to use a Raspberry Pi device for
permanently installed receivers. https://flightaware.com/adsb/piaware/build

FlightAware then announced that on September 15, 2016 this app would be released as Open Source, and rebranded as
"ADS-B Flight Scanner".  Additionally, the Open Source version would remove its support for uploading
aircraft positions to the FlightAware network, due to known data quality and backend support burden issues.
https://discussions.flightaware.com/ads-b-flight-tracking-f21/flightfeeder-android-update-t37736.html

The Open Source version of the app is not expected to be updated by FlightAware after it has been
made available, but other interested developers are encouraged to fork it and release their
own creative derivatives, in compliance with the license terms of the GNU GENERAL PUBLIC LICENSE v2. 
It is requested that all new forks use their own application name and package namespace.

