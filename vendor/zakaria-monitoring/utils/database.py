"""
Database management for Crash game data storage.
Supports PostgreSQL primarily for real-time monitoring.
"""

from pathlib import Path
from typing import List, Dict
from datetime import datetime
import pandas as pd
from sqlalchemy import create_engine
import yaml
import os

from utils.logger import log


class DatabaseManager:
    """Manages database connections and operations for crash game data."""
    
    def __init__(self, config_path: str = "config/config.yaml"):
        """
        Initialize database manager with configuration.
        
        Args:
            config_path: Path to configuration file
        """
        self.config = self._load_config(config_path)
        self.engine = self._create_engine()
        
    def _load_config(self, config_path: str) -> dict:
        """Load optional YAML configuration; credentials must come from environment."""
        try:
            with open(config_path, "r", encoding="utf-8") as handle:
                return yaml.safe_load(handle) or {}
        except FileNotFoundError:
            return {}

    def _create_engine(self):
        """Create a PostgreSQL engine from DATABASE_URL or explicit env variables."""
        from sqlalchemy.engine import URL

        database_url = os.getenv("DATABASE_URL", "").strip()
        if database_url:
            return create_engine(database_url, echo=False, pool_pre_ping=True)

        names = ("DATABASE_USER", "DATABASE_PASSWORD", "DATABASE_HOST",
                 "DATABASE_PORT", "DATABASE_NAME")
        missing = [name for name in names if not os.getenv(name)]
        if missing:
            raise RuntimeError("Missing database environment configuration: " + ", ".join(missing))

        connection_url = URL.create(
            "postgresql+psycopg2",
            username=os.environ["DATABASE_USER"],
            password=os.environ["DATABASE_PASSWORD"],
            host=os.environ["DATABASE_HOST"],
            port=int(os.environ["DATABASE_PORT"]),
            database=os.environ["DATABASE_NAME"],
        )
        log.info("PostgreSQL connection configured.")
        return create_engine(connection_url, echo=False, pool_pre_ping=True)

    def get_latest_games(self, limit: int = 100) -> pd.DataFrame:
        """
        Retrieve the most recent games.
        
        Args:
            limit: Maximum number of games to retrieve
            
        Returns:
            DataFrame with game data
        """
        query = f"SELECT * FROM crash_games ORDER BY timestamp DESC LIMIT {limit}"
        return pd.read_sql(query, self.engine)
    
    def get_games_by_date_range(self, start_date: datetime, end_date: datetime) -> pd.DataFrame:
        """
        Retrieve games within a date range.
        
        Args:
            start_date: Start of date range
            end_date: End of date range
            
        Returns:
            DataFrame with game data
        """
        query = """
            SELECT * FROM crash_games 
            WHERE timestamp BETWEEN %s AND %s
            ORDER BY timestamp
        """
        return pd.read_sql(query, self.engine, params=(start_date, end_date))
    
    def fetch_dataframe(self, query: str) -> pd.DataFrame:
        """
        Execute a SQL query and return results as DataFrame.
        
        Args:
            query: SQL query string
            
        Returns:
            DataFrame with query results
        """
        try:
            return pd.read_sql(query, self.engine)
        except Exception as e:
            log.error(f"Error executing query: {e}")
            return pd.DataFrame()
