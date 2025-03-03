import os

import pandas as pd
import psutil
import uvicorn
from fastapi import FastAPI, UploadFile, File

from src.api_methods import APIMethods
from src.logger import LoggerFactory
from src.config import api_config

app_logger = LoggerFactory.get_logger('APP')
app = FastAPI(openapi_url="/api/v1/openapi.json")
# Настроим API методы
APIMethods.setting(**api_config)


@app.api_route("/health", methods=['GET', 'HEAD'], tags=["health"])
def health():
    """
        Проверка доступности сервера.
    """
    process = psutil.Process(os.getpid())
    info = {
        "mem": f"{process.memory_info().rss / (1024 ** 2):.3f} MiB",
        "cpu_usage": process.cpu_percent(),
        "threads": len(process.threads()),
    }
    return {
        "status": "OK",
        "info": info
    }


@app.get("/datasets", tags=["datasets"])
def dataset_names_route():
    """ Получение имён наборов данных """
    return APIMethods.get_dataset_names()


@app.get("/datasets/configs", tags=["datasets"])
def dataset_configs_route():
    """
    Получение конфигурация наборов данных

    Returns:
        (string) Json вида {
            <dataset_name>: <dataset_config>
        }
    """
    return APIMethods.get_dataset_configs()


@app.post("/predict", tags=["models"])
def predict_route(
        dataset_name: str,
        data_file: UploadFile = File(...),
):
    """
    Предсказание данных из `data_file`файла моделью, обученной на `dataset_name` наборе данных.

    Args:
        dataset_name (string): Имя набора данных
        data_file (file): Файл формата .csv, содержащий столбцы признаков из обучающего набора данных

    Returns:
        (string) Json массив предсказанных значений для каждой строки из data
    """
    data = pd.read_csv(data_file.file)
    result_array = APIMethods.get_predict(
        data,
        dataset_name=dataset_name
    )
    return result_array.tolist()


if __name__ == '__main__':
    uvicorn.run(
        f"{__name__}:app",
        host=api_config.get("host", "localhost"),
        port=int(api_config.get("port", 8080)),
        log_level="info"
    )
