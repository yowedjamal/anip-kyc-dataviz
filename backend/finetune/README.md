# Fine-tuning Models for ANIP KYC

Ce dossier contient les scripts et notebooks pour le fine-tuning des modèles IA utilisés dans le projet.

## Structure
- `facial/` : Fine-tuning FaceNet/ArcFace pour la reconnaissance faciale
- `age/` : Fine-tuning CNN pour l'estimation d'âge
- `fraud/` : Fine-tuning CNN pour la détection de fraude documentaire

## Usage général
1. Placez les datasets Kaggle dans le dossier `data/` ou `train_images/` correspondant.
2. Préparez un CSV de paires pour la reconnaissance faciale (`img1,img2,label`).
3. Lancez les scripts Python pour entraîner les modèles.
4. Exportez les modèles au format ONNX ou SavedModel.
5. Intégrez-les dans le backend Java via ONNX Runtime ou un microservice Python.

## Exemples
Voir chaque sous-dossier pour des scripts détaillés.
