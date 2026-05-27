import os
import socket
import firebase_admin
from firebase_admin import credentials, firestore
from dotenv import load_dotenv
from contextlib import asynccontextmanager
from fastapi import FastAPI


def load_environment():
    load_dotenv()
    if not os.getenv("GOOGLE_API_KEY"):
        raise ValueError("GOOGLE_API_KEY is missing. Please check your .env file.")


try:
    cred = credentials.Certificate("serviceAccountKey.json")
    firebase_admin.initialize_app(cred)
except ValueError:
    pass


def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('10.255.255.255', 1))
        ip = s.getsockname()[0]
    except Exception:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip


def update_firebase_ip():
    db = firestore.client()
    local_ip = get_local_ip()
    db.collection("server_config").document("api_settings").set({
        "current_base_url": f"http://{local_ip}:8000/"
    })
    print(f"🚀 Server IP ({local_ip}) published to Firebase!")


@asynccontextmanager
async def lifespan(app: FastAPI):
    print("Starting up server...")
    update_firebase_ip()
    yield
    print("Shutting down server...")
