
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

1. **MIRLC2 Extension + Freesound quark**: Make sure you have the SuperCollider extension [MIRLC2](../extensions/MIRLC2) installed and working. This includes:
	* Make sure you have an Internet connection.
	* Make sure you have a [Freesound.org](http://freesound.org) account.
	* Make sure to obtain an [API key](https://freesound.org/help/developers/).
	* Install [Freesound quark](https://github.com/g-roma/Freesound.sc), which is a SuperCollider client for accessing the Freesound API and operate with sounds from Freesound.org. **Note**: For Windows/Linux users: "curl" should be installed in your computer so that Freesound.quark and the related extensions work. 
	* In order to connect with Freesound.org, the type of authentication used in MIRLC is Token. Make sure to introduce your API key in the class `MIRLC2.sc` and recompile the class library.
2. **FluCoMa**: Make sure you have the FluCoMa library installed.  Official link coming soon. 
3. **File Directories**: When instantiating the class in performance/training mode, it is recommended that you define the 3 folders (see the example in the HelpFile):
	* **Downloads**: the sounds are downloaded (write mode).
	* **Sound credits**: the sound credits list is stored (write mode).
	* **Machine learning models**: the models are loaded from (read mode).

## JSON files

You will also need 3 JSON files provided in this repo under the JSON folder: `model.JSON`, `standardizer.JSON`, and `pca.JSON`. They need to be in a defined folder: either use the argument `modelsPath` when instantiating a MIRLCa class to point to a customised folder, or place the 3 JSON files in the default `modelsPath` folder, which is the default temp directory `path = (Platform.defaultTempDir)`). 

The 3 JSON files have been generated using the FluCoMa library and are an illustrative model that you can use in performance mode. Once you train your model, you can replace the files with your own model.


License
----

The MIT License (MIT).
