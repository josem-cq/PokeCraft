import os
import shutil

# Configura la ruta de tu proyecto y la carpeta de destino
ORIGEN = r"D:\Archivos\Documentos\GitHub\PokeCraft"       # Ejemplo: "./mi-proyecto"
DESTINO = r"D:\Archivos\Documentos\GitHub\PokeCrafttxt"    # Ejemplo: "./mi-proyecto-en-txt"

# Extensiones o carpetas que deseas ignorar (opcional)
EXCLUIR_CARPETAS = {'.git', 'node_modules', '__pycache__', 'venv', '.vs', '.vscode'}
EXCLUIR_ARCHIVOS = {'.py','.pyc', '.DS_Store'}

def copiar_y_convertir():
    if os.path.exists(DESTINO):
        shutil.rmtree(DESTINO)
    
    for root, dirs, files in os.walk(ORIGEN):
        # Filtrar carpetas excluidas
        dirs[:] = [d for d in dirs if d not in EXCLUIR_CARPETAS]
        
        for file in files:
            if file in EXCLUIR_ARCHIVOS:
                continue
                
            ruta_origen = os.path.join(root, file)
            
            # Calcular ruta relativa para replicar la estructura de carpetas
            rel_path = os.path.relpath(ruta_origen, ORIGEN)
            ruta_destino = os.path.join(DESTINO, rel_path + ".txt")
            
            # Crear directorio de destino si no existe
            os.makedirs(os.path.dirname(ruta_destino), exist_ok=True)
            
            # Copiar el archivo como .txt
            shutil.copy2(ruta_origen, ruta_destino)
            print(f"Copiado: {rel_path} -> .txt")

if __name__ == "__main__":
    copiar_y_convertir()
    print("\n¡Proceso completado con éxito!")