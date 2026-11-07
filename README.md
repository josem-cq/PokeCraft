# PokeCraft 🎒🔴

**PokeCraft** es un mod de Minecraft desarrollado para **NeoForge 1.21.1** que introduce mecánicas únicas inspiradas en la captura de criaturas, junto con herramientas de utilidad avanzada como aljabas, tarros de mobs decorativos y un sistema completo de Poké Balls con diferentes tiers de efectividad.

---

## 🚀 Características Principales

* **Sistema de Poké Balls**: Incluye múltiples tiers (`Pokeball`, `Greatball`, `Superball`, `Ultraball` y `Masterball`) implementados como entidades de proyectil con probabilidades de captura dinámicas, herencia de nombres en yunque, y persistencia de datos mediante componentes modernos (`CustomData`).
* **Tarros de Mobs (`Mob Jar`)**: Bloques de tipo `BaseEntityBlock` con entidades de bloque y renderizado personalizado en el cliente (`MobJarRenderer`) que permiten almacenar y visualizar entidades vivas de forma estática en su interior.
* **Aljabas (`Quiver`)**: Elementos de utilidad enfocados en la gestión de inventario y arquería.
* **Pestaña Creativa Exclusiva**: Todos los ítems, bloques y herramientas del mod se agrupan ordenadamente en una categoría propia dentro del inventario creativo de Minecraft.

---

## 📂 Estructura del Proyecto

El código fuente y los recursos del mod se distribuyen bajo la siguiente estructura que se encuentra en estructura/ProjectStructure.txt

## 🦊 README.md de Neoforge 

Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/
