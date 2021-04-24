
MIRLCa
===
A SuperCollider extension that inherits from MIRLCRep2 class and expands its capabilities by proposing a virtual agent that embodies machine learning techniques. 

(c) 2020 by Anna Xambó (<anna.xambo@dmu.ac.uk>).


Introduction
----

The extension MIRLCa.sc is a follow-up work-in-progress of MIRLCRep.sc and MIRLCRep2. It includes a virtual agent that suggests similar sounds from Freesound.org based on your musical taste. This extension is still under development.


Application Start
----

Drag the MIRLCa folder to the Extensions folder of SuperCollider (suggested to create a subfolder with the same name): `/Users/{username}/Library/Application Support/SuperCollider/Extensions` (in Mac). If you are unsure where is this folder, open SuperCollider and type: `Platform.userExtensionDir`.  Once the class folder is placed in the Extensions folder, remember to either recompile the class library (`Language>Recompile Class Library`) or restart SuperCollider so that the class is recognised by SuperCollider.

## Requirements

* Make sure you have the SuperCollider extension MIRLC2 installed and working.
* It is recommended that you define the 3 folders where:
	* the sounds are downloaded (write mode).
	* the sound credits list is stored (write mode).
	* the models are loaded from (read mode).

## JSON files

You will also need 3 JSON files provided in this repo under the JSON folder: `model.JSON`, `standardizer.JSON`, and `pca.JSON`. They need to be in a defined folder: either use the argument `modelsPath` when instantiating a MIRLCa class to point to a customised folder, or place the 3 JSON files in the default `modelsPath` folder, which is the default temp directory `path = (Platform.defaultTempDir)`). 

The 3 JSON files have been generated using the FluCoMa library and are an illustrative model that you can use in performance mode. Once you train your model, you can replace the files with your own model.


License
----

The MIT License (MIT).
