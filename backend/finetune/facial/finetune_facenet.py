
"""
Script complet de fine-tuning pour la reconnaissance faciale (FaceNet/InceptionResnetV1).
Inclut : Dataset PyTorch pour paires d’images, modèle, boucle d’entraînement, export ONNX.
Adaptez le dataset et le modèle selon vos besoins réels.
"""
import torch
from torch.utils.data import Dataset, DataLoader
from torchvision import transforms
from PIL import Image
import pandas as pd
import os
from facenet_pytorch import InceptionResnetV1

# 1. Dataset personnalisé pour les paires d'images (img1, img2, label)
class FacePairDataset(Dataset):
    def __init__(self, csv_file, img_dir, transform=None):
        self.data = pd.read_csv(csv_file)
        self.img_dir = img_dir
        self.transform = transform or transforms.Compose([
            transforms.Resize((160, 160)),
            transforms.ToTensor(),
        ])

    def __len__(self):
        return len(self.data)

    def __getitem__(self, idx):
        row = self.data.iloc[idx]
        img1 = Image.open(os.path.join(self.img_dir, row[0])).convert('RGB')
        img2 = Image.open(os.path.join(self.img_dir, row[1])).convert('RGB')
        label = float(row[2])
        if self.transform:
            img1 = self.transform(img1)
            img2 = self.transform(img2)
        return (img1, img2), label

# 2. Modèle Siamese basé sur FaceNet
class FaceNetSiamese(torch.nn.Module):
    def __init__(self):
        super().__init__()
        self.backbone = InceptionResnetV1(pretrained='vggface2')
    def forward(self, x1, x2):
        emb1 = self.backbone(x1)
        emb2 = self.backbone(x2)
        return emb1, emb2

# 3. Contrastive Loss
def contrastive_loss(emb1, emb2, label, margin=1.0):
    d = torch.nn.functional.pairwise_distance(emb1, emb2)
    loss = label * d.pow(2) + (1 - label) * torch.clamp(margin - d, min=0).pow(2)
    return loss.mean()

# 4. Boucle d'entraînement
def train(model, dataloader, optimizer, device):
    model.train()
    running_loss = 0.0
    for (img1, img2), label in dataloader:
        img1, img2, label = img1.to(device), img2.to(device), label.to(device)
        optimizer.zero_grad()
        emb1, emb2 = model(img1, img2)
        loss = contrastive_loss(emb1, emb2, label)
        loss.backward()
        optimizer.step()
        running_loss += loss.item() * img1.size(0)
    return running_loss / len(dataloader.dataset)

if __name__ == "__main__":
    # Paramètres
    csv_file = "dataset_template.csv"  # À adapter (colonnes: img1,img2,label)
    img_dir = "./"  # À adapter
    batch_size = 8
    epochs = 5
    lr = 1e-4
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    # Dataset et DataLoader
    dataset = FacePairDataset(csv_file, img_dir)
    dataloader = DataLoader(dataset, batch_size=batch_size, shuffle=True)

    # Modèle, optim
    model = FaceNetSiamese().to(device)
    optimizer = torch.optim.Adam(model.parameters(), lr=lr)

    # Entraînement
    for epoch in range(epochs):
        loss = train(model, dataloader, optimizer, device)
        print(f"Epoch {epoch+1}/{epochs} - Loss: {loss:.4f}")

    # Export ONNX (pour une seule image, pas le mode siamese)
    dummy_input = torch.randn(1, 3, 160, 160, device=device)
    torch.onnx.export(model.backbone, dummy_input, "facenet_model.onnx", input_names=["input"], output_names=["embedding"], opset_version=11)
    print("Modèle exporté en facenet_model.onnx")
