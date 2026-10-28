package net.neoforged.fml;
import net.neoforged.neoforgespi.language.IModInfo;
public class ModContainer {
 final String version;public ModContainer(String s){version=s;}
 public IModInfo getModInfo(){return ()->new org.apache.maven.artifact.versioning.ArtifactVersion(){public String toString(){return version;}};}
}
