import os
from dotenv import load_dotenv


def load_environment():
    load_dotenv()

    if not os.getenv("GOOGLE_API_KEY"):
        raise ValueError("GOOGLE_API_KEY is missing. Please check your .env file.")