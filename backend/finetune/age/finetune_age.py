
"""
Script complet de fine-tuning pour l’estimation d’âge à partir d’images faciales.
Inclut : Dataset PyTorch, modèle ResNet18, boucle d'entraînement, export ONNX.
Adaptez le dataset et le modèle selon vos besoins réels.
"""
import torch
from torch.utils.data import Dataset, DataLoader
from torchvision import transforms, models
from PIL import Image
import pandas as pd
import os

class AgeDataset(Dataset):
    def __init__(self, csv_file, img_dir, transform=None):
        self.data = pd.read_csv(csv_file)
        self.img_dir = img_dir
        self.transform = transform or transforms.Compose([
            transforms.Resize((224, 224)),
            transforms.ToTensor(),
        ])

    def __len__(self):
        return len(self.data)

    def __getitem__(self, idx):
        img_path = os.path.join(self.img_dir, self.data.iloc[idx, 0])
        image = Image.open(img_path).convert('RGB')
        age = float(self.data.iloc[idx, 1])
        if self.transform:
            image = self.transform(image)
        return image, age

class AgeEstimationModel(torch.nn.Module):
    def __init__(self):
        super().__init__()
        self.base = models.resnet18(weights=None)  # Remplacer par weights="IMAGENET1K_V1" si besoin
        self.base.fc = torch.nn.Linear(self.base.fc.in_features, 1)

    def forward(self, x):
        return self.base(x).squeeze(1)

def train(model, dataloader, criterion, optimizer, device):
    model.train()
    running_loss = 0.0
    for images, ages in dataloader:
        images, ages = images.to(device), ages.float().to(device)
        optimizer.zero_grad()
        outputs = model(images)
        loss = criterion(outputs, ages)
        loss.backward()
        optimizer.step()
        running_loss += loss.item() * images.size(0)
    return running_loss / len(dataloader.dataset)

if __name__ == "__main__":
    # Paramètres
    csv_file = "dataset_template.csv"  # À adapter
    img_dir = "./"  # À adapter
    batch_size = 8
    epochs = 5
    lr = 1e-3
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    # Dataset et DataLoader
    dataset = AgeDataset(csv_file, img_dir)
    dataloader = DataLoader(dataset, batch_size=batch_size, shuffle=True)

    # Modèle, loss, optim
    model = AgeEstimationModel().to(device)
    criterion = torch.nn.MSELoss()
    optimizer = torch.optim.Adam(model.parameters(), lr=lr)

    # Entraînement
    for epoch in range(epochs):
        loss = train(model, dataloader, criterion, optimizer, device)
        print(f"Epoch {epoch+1}/{epochs} - Loss: {loss:.4f}")

    # Export ONNX
    dummy_input = torch.randn(1, 3, 224, 224, device=device)
    torch.onnx.export(model, dummy_input, "age_model.onnx", input_names=["input"], output_names=["output"], opset_version=11)
    print("Modèle exporté en age_model.onnx")
