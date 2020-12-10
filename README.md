
MIRLCa
===
A SuperCollider extension that inherits from MIRLCRep2 class and expands its capabilities by proposing a virtual agent that embodies machine learning techniques. 

(c) 2020 by Anna Xambó (<anna.xambo@dmu.ac.uk>).


Introduction
----

The extension MIRLCa.sc is a follow-up work-in-progress of MIRLCRep.sc and MIRLCRep2. It includes a virtual agent that suggests similar sounds from Freesound.org based on your musical taste. This extension is still under development and will be publicly released soon.

Application Start
----

Drag the MIRLCa file to the Extensions folder of SuperCollider (suggested to create a subfolder with the same name): `/Users/{username}/Library/Application Support/SuperCollider/Extensions` (in Mac). If you are unsure where is this folder, open SuperCollider and type: Platform.userExtensionDir

You will also need 3 JSON files generated from the FluCoMa library `model.JSON`, `standardizer.JSON`, and `pca.JSON`), which need to be saved in your MIRLC folder (if you are unsure just use the default temp directory `path = (Platform.defaultTempDir)`). A default collection of 3 files has been generated so that you can try the extension.

Remember to either recompile the class library (`Language>Recompile Class Library`) or restart SuperCollider.


License
----

The MIT License (MIT).
